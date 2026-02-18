package de.haberland.meicaller.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
)

@Composable
fun CallLogTabScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by UiSettingsStore.flow(context).collectAsState(initial = null)

    val hasCallLog = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Auto-Refresh wenn man zur App zurückkehrt (z.B. nach Kontakt speichern)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTrigger++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val calls by produceState(initialValue = emptyList(), hasCallLog, hasContacts, refreshTrigger) {
        value = if (hasCallLog) loadCallLog(context) else emptyList()
    }

    var pendingBgKey by remember { mutableStateOf<String?>(null) }
    val pickBg = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
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
            IconButton(onClick = { refreshTrigger++ }) { Icon(Icons.Filled.Refresh, contentDescription = null) }
        }

        if (!hasCallLog) {
            Text("Berechtigung für Anrufliste fehlt.", modifier = Modifier.padding(16.dp))
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(calls) { item ->
                val normalized = remember(item.number) { normalizeForCompare(item.number) }
                val bgUri by ContactBackgroundStore.backgroundUriFlow(context, normalized).collectAsState(initial = null)

                CallLogRow(
                    item = item,
                    accentColor = accentColor,
                    hasCustomBackground = !bgUri.isNullOrBlank(),
                    onCall = { placeCall(context, item.number) },
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
        modifier = Modifier.fillMaxWidth().clickable { onCall() },
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
                Text("${item.number} · $dateText", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (!item.isContact) {
                IconButton(onClick = onAddContact) { Icon(Icons.Filled.PersonAdd, contentDescription = null) }
            }

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

private suspend fun loadCallLog(context: Context, limit: Int = 120): List<CallLogItem> = withContext(Dispatchers.IO) {
    val out = mutableListOf<CallLogItem>()
    val hasReadContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
        null, null, "${CallLog.Calls.DATE} DESC"
    )?.use { c ->
        while (c.moveToNext() && out.size < limit) {
            val num = c.getString(1) ?: continue
            var name = c.getString(0)?.takeIf { it.isNotBlank() }
            if (name == null && hasReadContacts) name = getContactName(context, num)
            out.add(CallLogItem(name ?: num, num, c.getLong(2), c.getInt(3), name != null))
        }
    }
    out
}
