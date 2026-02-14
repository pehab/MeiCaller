package de.haberland.meicaller.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import de.haberland.meicaller.data.ContactBackgroundStore
import de.haberland.meicaller.util.normalizeForCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a single entry in the system call log.
 * @property nameOrNumber The contact name if available, otherwise the phone number.
 * @property number The raw phone number.
 * @property dateMillis The timestamp of the call in milliseconds.
 * @property type The type of call (incoming, outgoing, missed, etc.).
 * @property isContact True if the number is already known as a contact.
 */
data class CallLogItem(
    val nameOrNumber: String,
    val number: String,
    val dateMillis: Long,
    val type: Int,
    val isContact: Boolean,
)

/**
 * Screen displaying the list of recent calls.
 * Allows users to view call history, place calls, and assign custom background images to numbers.
 */
@Composable
fun CallLogTabScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val hasCallLog =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    var refresh by remember { mutableIntStateOf(0) }

    // State representing the list of calls loaded from the system provider
    val calls by produceState(initialValue = emptyList(), hasCallLog, refresh) {
        value = if (hasCallLog) loadCallLog(context) else emptyList()
    }

    // State for the number currently being edited for a custom background
    var pendingBgKey by remember { mutableStateOf<String?>(null) }

    // File picker launcher for selecting background images
    val pickBg =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            val key = pendingBgKey
            if (uri != null && !key.isNullOrBlank()) {
                try {
                    // Try to persist the permission so the image remains accessible after reboot
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: Throwable) {
                    // Some providers don't support persistable permission; still store the uri.
                }

                scope.launch {
                    ContactBackgroundStore.setBackground(context, key, uri)
                }
            }
        }

    Column(
        Modifier
            .fillMaxSize()
            .padding(14.dp),
    ) {
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

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(calls) { item ->
                val normalized = remember(item.number) { normalizeForCompare(item.number) }
                val bgUri by ContactBackgroundStore
                    .backgroundUriFlow(context, normalized)
                    .collectAsState(initial = null)

                CallLogRow(
                    item = item,
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

/**
 * A single row representing a call in the log.
 * Shows call type icon, name/number, and timestamp.
 */
@Composable
private fun CallLogRow(
    item: CallLogItem,
    hasCustomBackground: Boolean,
    onCall: () -> Unit,
    onAddContact: () -> Unit,
    onSetBackground: () -> Unit,
    onClearBackground: () -> Unit,
) {
    val df = remember { SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()) }
    val dateText = remember(item.dateMillis) { df.format(Date(item.dateMillis)) }

    val (icon, label, isMissed) =
        when (item.type) {
            CallLog.Calls.INCOMING_TYPE -> Triple(Icons.AutoMirrored.Filled.CallReceived, "Eingehend", false)
            CallLog.Calls.OUTGOING_TYPE -> Triple(Icons.AutoMirrored.Filled.CallMade, "Ausgehend", false)
            CallLog.Calls.MISSED_TYPE -> Triple(Icons.AutoMirrored.Filled.CallMissed, "Verpasst", true)
            else -> Triple(Icons.Filled.Call, "Anruf", false)
        }

    val containerColor =
        if (isMissed) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surface
        }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCall() },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                tonalElevation = 2.dp,
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
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ➕ Add Contact if not present
            if (!item.isContact) {
                IconButton(onClick = onAddContact) {
                    Icon(
                        Icons.Filled.PersonAdd,
                        contentDescription = "Kontakt hinzufügen",
                    )
                }
            }

            // 🎨 Set / Clear background
            IconButton(onClick = onSetBackground) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = "Hintergrund setzen",
                )
            }
            if (hasCustomBackground) {
                IconButton(onClick = onClearBackground) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Hintergrund entfernen",
                    )
                }
            }

            IconButton(onClick = onCall) {
                Icon(Icons.Filled.Call, contentDescription = "Anrufen")
            }
        }
    }
}

/**
 * Loads recent calls from the system's CallLog provider.
 * @param limit Maximum number of entries to fetch.
 */
private suspend fun loadCallLog(
    context: Context,
    limit: Int = 120,
): List<CallLogItem> =
    withContext(Dispatchers.IO) {
        val out = mutableListOf<CallLogItem>()
        val cr = context.contentResolver

        val projection =
            arrayOf(
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE,
            )

        cr
            .query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val cachedName = c.getString(0)
                    val number = c.getString(1) ?: continue
                    val date = c.getLong(2)
                    val type = c.getInt(3)

                    val hasName = !cachedName.isNullOrBlank()

                    out.add(
                        CallLogItem(
                            nameOrNumber = if (hasName) cachedName else number,
                            number = number,
                            dateMillis = date,
                            type = type,
                            isContact = hasName,
                        ),
                    )
                }
            }

        out
    }

/**
 * Places a call using the TelecomManager if permissions allow,
 * otherwise falls back to showing the system dialer.
 */
private fun placeCall(
    context: Context,
    number: String,
) {
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

/**
 * Opens the system "Add Contact" screen for a given number.
 */
private fun addToContacts(
    context: Context,
    number: String,
) {
    val intent =
        Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        }
    context.startActivity(intent)
}
