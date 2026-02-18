package de.haberland.meicaller.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import de.haberland.meicaller.data.UiSettings
import de.haberland.meicaller.util.getContactInfo
import de.haberland.meicaller.util.normalizeForCompare
import de.haberland.meicaller.util.placeCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DialerTabScreen(
    settings: UiSettings,
    initialNumber: String = "",
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var number by remember { mutableStateOf(initialNumber) }
    var pendingCall by remember { mutableStateOf<String?>(null) }
    var refreshSignal by remember { mutableIntStateOf(0) }

    // Listen for contact and call log changes
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshSignal++
            }
        }
        context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
        context.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    val requestCallPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val n = pendingCall
        if (granted && n != null) placeCall(context, n)
    }

    val requestCallLogPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val requestContactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun callWithHaptic(n: String) {
        if (n.isBlank()) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            placeCall(context, n)
        } else {
            pendingCall = n
            requestCallPermission.launch(Manifest.permission.CALL_PHONE)
        }
    }

    val hasCallLogPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    val hasContactsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    val suggestions by produceState(initialValue = emptyList(), number, hasCallLogPermission, hasContactsPermission, refreshSignal) {
        value = loadSuggestions(context, number, hasCallLogPermission, hasContactsPermission)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        // Top Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (number.isBlank()) "Vorgeschlagen" else "Treffer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            if (!hasCallLogPermission || !hasContactsPermission) {
                TextButton(onClick = {
                    if (!hasCallLogPermission) requestCallLogPermission.launch(Manifest.permission.READ_CALL_LOG)
                    else requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
                }) {
                    Text("Berechtigung fehlt", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Suggestions List with Animation
        Box(modifier = Modifier.weight(1f)) {
            SuggestionsCard(
                items = suggestions,
                onPick = { picked -> 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    number = picked 
                },
                onCall = { picked -> callWithHaptic(picked) }
            )
        }

        // Bottom Dialer Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                placeholder = { Text("Nummer oder Name", style = MaterialTheme.typography.bodyLarge) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )

            Spacer(Modifier.height(16.dp))

            DialPad(
                onKey = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    number += it 
                },
                onBackspace = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (number.isNotEmpty()) number = number.dropLast(1) 
                },
                onClear = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    number = "" 
                }
            )

            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(
                visible = number.isNotBlank(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                CallButton(
                    acceptImageUri = settings.acceptButtonUri,
                    onClick = { callWithHaptic(number) }
                )
            }
            
            if (number.isBlank()) {
                Spacer(Modifier.height(84.dp)) // Placeholder for call button height
            }
        }
    }
}

private enum class SuggestionSource { RECENT, CONTACT }
private data class SuggestionItem(val title: String, val subtitle: String, val number: String, val source: SuggestionSource, val photoUri: String? = null)

@Composable
private fun SuggestionsCard(
    items: List<SuggestionItem>,
    onPick: (String) -> Unit,
    onCall: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
    ) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Keine Treffer", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(items) { idx, item ->
                    SuggestionRow(item, onPick, onCall)
                    if (idx != items.lastIndex) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(item: SuggestionItem, onPick: (String) -> Unit, onCall: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(item.number) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (item.photoUri != null) {
                AsyncImage(model = item.photoUri, contentDescription = null, contentScale = ContentScale.Crop)
            } else {
                Text(item.title.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }

        IconButton(onClick = { onCall(item.number) }) {
            Icon(Icons.Filled.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DialPad(onKey: (String) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#"))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    DialButton(key, onClick = { onKey(key) }, modifier = Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.weight(1f))
            DialButton("⌫", onClick = onBackspace, modifier = Modifier.weight(1f), isAction = true)
            DialButton("C", onClick = onClear, modifier = Modifier.weight(1f), isAction = true)
        }
    }
}

@Composable
private fun DialButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, isAction: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (isAction) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        tonalElevation = if (isAction) 2.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.headlineMedium.copy(fontSize = if (isAction) 24.sp else 28.sp))
        }
    }
}

@Composable
private fun CallButton(acceptImageUri: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 8.dp
    ) {
        if (!acceptImageUri.isNullOrBlank()) {
            AsyncImage(model = acceptImageUri, contentDescription = null, contentScale = ContentScale.Crop)
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

private suspend fun loadSuggestions(context: Context, query: String, canLog: Boolean, canContacts: Boolean): List<SuggestionItem> = withContext(Dispatchers.IO) {
    val q = query.trim()
    val results = mutableListOf<SuggestionItem>()
    if (q.isBlank()) {
        if (canLog) results += loadRecentCalls(context)
        return@withContext results
    }
    if (canContacts) results += searchContacts(context, q)
    if (canLog) results += searchCallLog(context, q)
    
    results
        .sortedBy { if (it.source == SuggestionSource.CONTACT) 0 else 1 }
        .distinctBy { normalizeForCompare(it.number) }
        .take(15)
}

private fun loadRecentCalls(context: Context): List<SuggestionItem> {
    val list = mutableListOf<SuggestionItem>()
    context.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER), null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
        while (c.moveToNext() && list.size < 10) {
            val num = c.getString(0) ?: continue
            val info = getContactInfo(context, num)
            list.add(SuggestionItem(info.name ?: num, num, num, SuggestionSource.RECENT, info.photoUri?.toString()))
        }
    }
    return list
}

private fun searchContacts(context: Context, q: String): List<SuggestionItem> {
    val list = mutableListOf<SuggestionItem>()
    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val proj = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
    val sel = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
    val args = arrayOf("%$q%", "%$q%")
    context.contentResolver.query(uri, proj, sel, args, null)?.use { c ->
        while (c.moveToNext() && list.size < 10) {
            val name = c.getString(0) ?: continue
            list.add(SuggestionItem(name, c.getString(1) ?: "", c.getString(1) ?: "", SuggestionSource.CONTACT, c.getString(2)))
        }
    }
    return list
}

private fun searchCallLog(context: Context, q: String): List<SuggestionItem> {
    val list = mutableListOf<SuggestionItem>()
    val sel = "${CallLog.Calls.CACHED_NAME} LIKE ? OR ${CallLog.Calls.NUMBER} LIKE ?"
    context.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER), sel, arrayOf("%$q%", "%$q%"), null)?.use { c ->
        while (c.moveToNext() && list.size < 10) {
            val num = c.getString(0) ?: continue
            val info = getContactInfo(context, num)
            list.add(SuggestionItem(info.name ?: num, num, num, SuggestionSource.RECENT, info.photoUri?.toString()))
        }
    }
    return list
}
