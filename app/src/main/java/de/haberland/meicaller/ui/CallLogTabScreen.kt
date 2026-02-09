package de.haberland.meicaller.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.telecom.TelecomManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import de.haberland.meicaller.data.UiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CallLogItem(
    val nameOrNumber: String,
    val number: String,
    val dateMillis: Long,
    val type: Int
)

@Composable
fun CallLogTabScreen(settings: UiSettings) {
    val context = LocalContext.current

    val hasCallLog =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED

    var refresh by remember { mutableIntStateOf(0) }

    val calls by produceState(initialValue = emptyList<CallLogItem>(), hasCallLog, refresh) {
        value = if (hasCallLog) loadCallLog(context, limit = 120) else emptyList()
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Anrufliste", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { refresh++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
            }
        }
        Spacer(Modifier.height(10.dp))

        if (!hasCallLog) {
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("MeiCaller braucht Zugriff auf die Anrufliste.")
                    Spacer(Modifier.height(8.dp))
                    Text("→ Bitte in den App-Berechtigungen „Anrufliste“ erlauben.")
                }
            }
            return
        }

        if (calls.isEmpty()) {
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Keine Einträge gefunden.")
                }
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(calls) { item ->
                CallLogRow(
                    item = item,
                    onCall = { placeCall(context, item.number) }
                )
            }
        }
    }
}

@Composable
private fun CallLogRow(
    item: CallLogItem,
    onCall: () -> Unit
) {
    val df = remember { SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()) }
    val dateText = remember(item.dateMillis) { df.format(Date(item.dateMillis)) }

    val (icon, label, isMissed) = when (item.type) {
        CallLog.Calls.INCOMING_TYPE -> Triple(Icons.Filled.CallReceived, "Eingehend", false)
        CallLog.Calls.OUTGOING_TYPE -> Triple(Icons.Filled.CallMade, "Ausgehend", false)
        CallLog.Calls.MISSED_TYPE -> Triple(Icons.Filled.CallMissed, "Verpasst", true)
        else -> Triple(Icons.Filled.Call, "Anruf", false)
    }

    val containerColor =
        if (isMissed) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surface

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCall() }   // ✅ Row = direkt anrufen
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = label)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(item.nameOrNumber, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${item.number} · $dateText",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onCall) { // ✅ Icon = auch anrufen
                Icon(Icons.Filled.Call, contentDescription = "Anrufen")
            }
        }
    }
}


private suspend fun loadCallLog(context: Context, limit: Int): List<CallLogItem> = withContext(Dispatchers.IO) {
    val out = mutableListOf<CallLogItem>()
    val cr = context.contentResolver

    val projection = arrayOf(
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.NUMBER,
        CallLog.Calls.DATE,
        CallLog.Calls.TYPE
    )

    cr.query(
        CallLog.Calls.CONTENT_URI,
        projection,
        null,
        null,
        "${CallLog.Calls.DATE} DESC"
    )?.use { c ->
        while (c.moveToNext() && out.size < limit) {
            val cachedName = c.getString(0)
            val number = c.getString(1) ?: continue
            val date = c.getLong(2)
            val type = c.getInt(3)

            out.add(
                CallLogItem(
                    nameOrNumber = cachedName?.takeIf { it.isNotBlank() } ?: number,
                    number = number,
                    dateMillis = date,
                    type = type
                )
            )
        }
    }

    out
}

private fun placeCall(context: Context, number: String) {
    val clean = number.trim()
    if (clean.isEmpty()) return

    val uri = "tel:$clean".toUri()
    val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    try {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            context.startActivity(Intent(Intent.ACTION_DIAL, uri))
            return
        }
        telecom.placeCall(uri, null)
    } catch (_: Throwable) {
        context.startActivity(Intent(Intent.ACTION_DIAL, uri))
    }
}
