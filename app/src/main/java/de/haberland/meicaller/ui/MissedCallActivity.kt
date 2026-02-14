package de.haberland.meicaller.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import de.haberland.meicaller.data.UiSettings
import de.haberland.meicaller.data.UiSettingsStore
import de.haberland.meicaller.ui.theme.MeiCallerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity that displays a list of missed calls.
 * It provides functionality to view missed calls and mark them as seen in the system call log.
 */
class MissedCallActivity : ComponentActivity() {
    private val requestCallLogPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* UI updates automatically via produceState */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settings by UiSettingsStore
                .flow(this)
                .collectAsState(initial = UiSettings())

            MeiCallerTheme(
                primaryHex = settings.primaryHex,
                accentHex = settings.accentHex,
            ) {
                Surface(Modifier.fillMaxSize()) {
                    MissedCallsScreen(
                        onBack = { finish() },
                        onRequestCallLog = {
                            requestCallLogPermission.launch(Manifest.permission.READ_CALL_LOG)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Data class representing a single missed call entry.
 */
private data class MissedCallItem(
    val nameOrNumber: String,
    val number: String,
    val dateMillis: Long,
    val isContact: Boolean,
)

/**
 * Main screen component for displaying missed calls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissedCallsScreen(
    onBack: () -> Unit,
    onRequestCallLog: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val hasCallLogPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    // WRITE_CALL_LOG is used to clear the "missed call" status in the system
    val hasWriteCallLogPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    var refreshTick by remember { mutableIntStateOf(0) }
    var didMarkSeen by remember { mutableStateOf(false) }

    // Load missed calls asynchronously when permission is granted or refresh is triggered
    val missedCalls by produceState(
        initialValue = emptyList(),
        hasCallLogPermission,
        refreshTick,
    ) {
        value = if (hasCallLogPermission) loadMissedCalls(context) else emptyList()
    }

    // Automatically mark missed calls as "seen" when the screen is viewed
    LaunchedEffect(hasCallLogPermission, missedCalls) {
        if (!didMarkSeen && hasCallLogPermission && missedCalls.isNotEmpty()) {
            val changed = markAllMissedAsSeen(context)
            if (changed) {
                refreshTick++ // Refresh UI to reflect "seen" status
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Verpasste Anrufe") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                // Try marking as seen one last time before exiting
                                try {
                                    markAllMissedAsSeen(context)
                                } catch (_: Throwable) {
                                }
                                onBack()
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTick++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier =
                Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .padding(14.dp),
        ) {
            if (!hasCallLogPermission) {
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("MeiCaller braucht Zugriff auf die Anrufliste, um verpasste Anrufe anzuzeigen.")
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onRequestCallLog, modifier = Modifier.fillMaxWidth()) {
                            Text("Anrufliste erlauben")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Hinweis: Zum „Zurückschalten“ (als gesehen markieren) kann WRITE_CALL_LOG nötig sein.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                return@Column
            }

            if (missedCalls.isEmpty()) {
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Keine verpassten Anrufe gefunden.")
                        Spacer(Modifier.height(6.dp))
                        Text("Tipp: Tippe auf ↻ zum Aktualisieren.", style = MaterialTheme.typography.bodySmall)
                        if (!hasWriteCallLogPermission) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Hinweis: Wenn „verpasst“ nicht verschwindet, fehlt evtl. WRITE_CALL_LOG.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                return@Column
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(missedCalls) { item ->
                    MissedCallRow(
                        item = item,
                        onOpenDialerWithNumber = {
                            // Opens the mini dialer to call back the number
                            val intent =
                                Intent(context, MiniDialerActivity::class.java).apply {
                                    data = "tel:${item.number}".toUri()
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            context.startActivity(intent)
                        },
                        onAddContact = { addToContacts(context, item.number) },
                    )
                }
            }
        }
    }
}

/**
 * A row representing a single missed call.
 */
@Composable
private fun MissedCallRow(
    item: MissedCallItem,
    onOpenDialerWithNumber: () -> Unit,
    onAddContact: () -> Unit,
) {
    val df = remember { SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()) }
    val dateText = remember(item.dateMillis) { df.format(Date(item.dateMillis)) }

    Card(
        shape = RoundedCornerShape(18.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onOpenDialerWithNumber() },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                tonalElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(item.nameOrNumber.take(1).uppercase())
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

            if (!item.isContact) {
                IconButton(onClick = onAddContact) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Kontakt hinzufügen")
                }
            }

            IconButton(onClick = onOpenDialerWithNumber) {
                Icon(Icons.Filled.Call, contentDescription = "Zurückrufen")
            }
        }
    }
}

/**
 * Loads recent missed calls from the system provider.
 */
private suspend fun loadMissedCalls(
    context: Context,
    limit: Int = 30,
): List<MissedCallItem> =
    withContext(Dispatchers.IO) {
        val out = mutableListOf<MissedCallItem>()
        val cr = context.contentResolver

        val projection =
            arrayOf(
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE,
            )

        val selection = "${CallLog.Calls.TYPE}=?"
        val args = arrayOf(CallLog.Calls.MISSED_TYPE.toString())

        cr
            .query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                args,
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val cachedName = c.getString(0)
                    val number = c.getString(1) ?: continue
                    val date = c.getLong(2)

                    val hasName = !cachedName.isNullOrBlank()

                    out.add(
                        MissedCallItem(
                            nameOrNumber = if (hasName) cachedName else number,
                            number = number,
                            dateMillis = date,
                            isContact = hasName,
                        ),
                    )
                }
            }
        out
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

/**
 * Marks all "new" missed calls as "seen/read" in the system call log.
 * @return true if any entries were updated.
 */
private suspend fun markAllMissedAsSeen(context: Context): Boolean =
    withContext(Dispatchers.IO) {
        fun countNewMissed(): Int {
            val cr = context.contentResolver
            val projection = arrayOf(CallLog.Calls._ID)
            val where = "${CallLog.Calls.TYPE}=? AND ${CallLog.Calls.NEW}=?"
            val args = arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "1")

            cr.query(CallLog.Calls.CONTENT_URI, projection, where, args, null)?.use { c ->
                return c.count
            }
            return 0
        }

        try {
            val before = countNewMissed()

            val cr = context.contentResolver
            // Mark as 'not new'
            val valuesNew = ContentValues().apply { put(CallLog.Calls.NEW, 0) }
            val updatedNew =
                cr.update(
                    CallLog.Calls.CONTENT_URI,
                    valuesNew,
                    "${CallLog.Calls.TYPE}=? AND ${CallLog.Calls.NEW}=?",
                    arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "1"),
                )

            // Mark as 'read'
            val valuesRead = ContentValues().apply { put("is_read", 1) }
            val updatedRead =
                cr.update(
                    CallLog.Calls.CONTENT_URI,
                    valuesRead,
                    "${CallLog.Calls.TYPE}=? AND (is_read=0 OR is_read IS NULL)",
                    arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                )

            // Notify providers to update system badges/UI
            cr.notifyChange(CallLog.Calls.CONTENT_URI, null)
            cr.notifyChange("content://call_log/calls".toUri(), null)
            cr.notifyChange("content://call_log/calls/".toUri(), null)

            val after = countNewMissed()

            android.util.Log.d(
                "MeiCaller",
                "markAllMissedAsSeen: beforeNewMissed=$before afterNewMissed=$after updatedNew=$updatedNew updatedRead=$updatedRead",
            )

            (updatedNew + updatedRead) > 0
        } catch (t: Throwable) {
            android.util.Log.w("MeiCaller", "markAllMissedAsSeen failed: ${t.message}", t)
            false
        }
    }
