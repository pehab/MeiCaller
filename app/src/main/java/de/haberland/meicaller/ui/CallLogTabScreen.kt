package de.haberland.meicaller.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.haberland.meicaller.data.ContactBackgroundStore
import de.haberland.meicaller.data.UiSettingsStore
import de.haberland.meicaller.util.addToContacts
import de.haberland.meicaller.util.getContactName
import de.haberland.meicaller.util.normalizeForCompare
import de.haberland.meicaller.util.placeCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CallLogItem(
    val nameOrNumber: String,
    val number: String,
    val dateMillis: Long,
    val type: Int,
    val isContact: Boolean,
    val numberVisible: Boolean,
)

@Composable
fun CallLogTabScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by UiSettingsStore.flow(context).collectAsState(initial = null)

    var hasCallLog by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED)
    }
    val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCallLog = granted
    }

    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Update on lifecycle resume
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTrigger++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Update when contacts or call log changes
    DisposableEffect(context, hasCallLog, hasContacts) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshTrigger++
            }
        }
        if (hasCallLog) {
            context.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        }
        if (hasContacts) {
            context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
        }
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    val calls by produceState(initialValue = emptyList(), hasCallLog, hasContacts, refreshTrigger) {
        value = if (hasCallLog) loadCallLog(context) else emptyList()
    }

    var pendingBgKey by remember { mutableStateOf<String?>(null) }
    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val key = pendingBgKey
        if (uri != null && !key.isNullOrBlank()) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            scope.launch { ContactBackgroundStore.setBackground(context, key, uri) }
        }
    }

    val accentColor = remember(settings?.accentHex) {
        try { Color((settings?.accentHex ?: "#7C4DFF").toColorInt()) }
        catch (_: Exception) { Color(0xFF7C4DFF) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Anrufliste", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (!hasCallLog) {
                TextButton(onClick = { requestPermission.launch(Manifest.permission.READ_CALL_LOG) }) {
                    Text("Berechtigung fehlt", style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = { refreshTrigger++ }) { Icon(Icons.Filled.Refresh, contentDescription = null) }
        }

        if (!hasCallLog) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Berechtigung für Anrufliste fehlt.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { requestPermission.launch(Manifest.permission.READ_CALL_LOG) }) {
                        Text("Berechtigung erteilen")
                    }
                }
            }
            return
        }

        if (calls.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Anrufliste ist leer.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(calls) { item ->
                    val normalized = remember(item.number) { normalizeForCompare(item.number) }
                    val bgUri by if (item.numberVisible) {
                        ContactBackgroundStore.backgroundUriFlow(context, normalized).collectAsState(initial = null)
                    } else remember { mutableStateOf(null) }

                    CallLogRow(
                        item = item,
                        accentColor = accentColor,
                        hasCustomBackground = !bgUri.isNullOrBlank(),
                        onCall = { if (item.numberVisible) placeCall(context, item.number) },
                        onAddContact = { addToContacts(context, item.number) },
                        onSetBackground = {
                            pendingBgKey = normalized
                            pickBg.launch(arrayOf("image/*"))
                        },
                        onClearBackground = {
                            scope.launch { ContactBackgroundStore.clearBackground(context, normalized) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CallLogRow(
    item: CallLogItem,
    accentColor: Color,
    hasCustomBackground: Boolean,
    onCall: () -> Unit,
    onAddContact: () -> Unit,
    onSetBackground: () -> Unit,
    onClearBackground: () -> Unit,
) {
    val df = remember { SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()) }
    val dateText = remember(item.dateMillis) { df.format(Date(item.dateMillis)) }

    val (icon, label, isMissed) = when (item.type) {
        CallLog.Calls.INCOMING_TYPE -> Triple(Icons.AutoMirrored.Filled.CallReceived, "Eingehend", false)
        CallLog.Calls.OUTGOING_TYPE -> Triple(Icons.AutoMirrored.Filled.CallMade, "Ausgehend", false)
        CallLog.Calls.MISSED_TYPE -> Triple(Icons.AutoMirrored.Filled.CallMissed, "Verpasst", true)
        else -> Triple(Icons.Filled.Call, "Anruf", false)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isMissed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(enabled = item.numberVisible) { onCall() },
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                tonalElevation = 2.dp,
                color = if (hasCustomBackground) accentColor else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = label, tint = if (hasCustomBackground) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(item.nameOrNumber, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isMissed) FontWeight.Bold else FontWeight.Normal)
                Text("${if (item.numberVisible) item.number else ""} · $dateText".trimStart(' ', '·'), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (!item.isContact && item.numberVisible) {
                IconButton(onClick = onAddContact) { Icon(Icons.Filled.PersonAdd, contentDescription = null) }
            }

            if (item.numberVisible) {
                IconButton(onClick = onSetBackground) {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = if (hasCustomBackground) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                if (hasCustomBackground) {
                    IconButton(onClick = onClearBackground) { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                }

                IconButton(onClick = onCall) { Icon(Icons.Filled.Call, contentDescription = null) }
            }
        }
    }
}

private suspend fun loadCallLog(context: Context, limit: Int = 120): List<CallLogItem> = withContext(Dispatchers.IO) {
    val out = mutableListOf<CallLogItem>()
    val hasReadContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    val projection = arrayOf(
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.NUMBER,
        CallLog.Calls.DATE,
        CallLog.Calls.TYPE,
        CallLog.Calls.NUMBER_PRESENTATION
    )

    try {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null, null, "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
            val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
            val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)
            val presIdx = c.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION)

            while (c.moveToNext() && out.size < limit) {
                val presentation = if (presIdx != -1) c.getInt(presIdx) else CallLog.Calls.PRESENTATION_ALLOWED
                val num = c.getString(numIdx) ?: ""
                val cachedName = if (nameIdx != -1) c.getString(nameIdx)?.takeIf { it.isNotBlank() } else null
                
                val isVisible = presentation == CallLog.Calls.PRESENTATION_ALLOWED && num.isNotBlank()
                
                // Real-time contact check to fix Bug 2
                val realName = if (isVisible && hasReadContacts) {
                    getContactName(context, num)
                } else null

                val finalName = when {
                    !isVisible -> when (presentation) {
                        CallLog.Calls.PRESENTATION_RESTRICTED -> "Private Nummer"
                        CallLog.Calls.PRESENTATION_PAYPHONE -> "Öffentliches Telefon"
                        else -> "Unbekannte Nummer"
                    }
                    realName != null -> realName
                    cachedName != null -> cachedName
                    else -> num
                }

                out.add(CallLogItem(
                    nameOrNumber = finalName,
                    number = if (isVisible) num else "",
                    dateMillis = c.getLong(dateIdx),
                    type = c.getInt(typeIdx),
                    isContact = realName != null || cachedName != null || (!isVisible),
                    numberVisible = isVisible
                ))
            }
        }
    } catch (_: SecurityException) {
        // Fallback or log
    }
    out
}
