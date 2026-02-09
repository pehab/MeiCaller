package de.haberland.meicaller.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import coil.compose.AsyncImage
import de.haberland.meicaller.data.UiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dialer-Tab Screen: Header/Settings kommt aus MainActivity → hier nur Content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerTabScreen(
    settings: UiSettings,
    initialNumber: String = ""
) {
    val context = LocalContext.current
    var number by remember { mutableStateOf(initialNumber) }
    var pendingCall by remember { mutableStateOf<String?>(null) }

    val requestCallPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val n = pendingCall
            pendingCall = null
            if (granted && n != null) placeCall(context, n)
        }

    val requestCallLogPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* UI updates */ }

    val requestContactsPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* UI updates */ }

    fun callOrAskPermission(n: String) {
        val clean = n.trim()
        if (clean.isEmpty()) return

        val hasPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                    PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            placeCall(context, clean)
        } else {
            pendingCall = clean
            requestCallPermission.launch(Manifest.permission.CALL_PHONE)
        }
    }

    val hasCallLogPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED

    val hasContactsPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED

    val suggestions by produceState(
        initialValue = emptyList<SuggestionItem>(),
        number,
        hasCallLogPermission,
        hasContactsPermission
    ) {
        value = loadSuggestions(
            context = context,
            query = number,
            canReadCallLog = hasCallLogPermission,
            canReadContacts = hasContactsPermission
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (number.isBlank()) "Vorgeschlagen" else "Treffer",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            when {
                !hasCallLogPermission -> TextButton(onClick = { requestCallLogPermission.launch(Manifest.permission.READ_CALL_LOG) }) {
                    Text("Anrufliste erlauben")
                }
                !hasContactsPermission -> TextButton(onClick = { requestContactsPermission.launch(Manifest.permission.READ_CONTACTS) }) {
                    Text("Kontakte erlauben")
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        SuggestionsCard(
            canReadCallLog = hasCallLogPermission,
            canReadContacts = hasContactsPermission,
            query = number,
            items = suggestions,
            onPick = { picked -> number = picked },
            onCall = { picked -> callOrAskPermission(picked) }
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            label = { Text("Nummer oder Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.weight(1f))

        DialPad(
            onKey = { number += it },
            onBackspace = { if (number.isNotEmpty()) number = number.dropLast(1) },
            onClear = { number = "" }
        )

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            CallButton(
                acceptImageUri = settings.acceptButtonUri,
                onClick = { callOrAskPermission(number) }
            )
        }
    }
}

private enum class SuggestionSource { RECENT, CONTACT }

private data class SuggestionItem(
    val title: String,
    val subtitle: String,
    val number: String,
    val source: SuggestionSource
)

@Composable
private fun SuggestionsCard(
    canReadCallLog: Boolean,
    canReadContacts: Boolean,
    query: String,
    items: List<SuggestionItem>,
    onPick: (String) -> Unit,
    onCall: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
    ) {
        when {
            query.isBlank() && !canReadCallLog -> {
                Column(Modifier.padding(14.dp)) {
                    Text("Für „Vorgeschlagen“ (letzte Anrufe) braucht MeiCaller Zugriff auf die Anrufliste.")
                    Spacer(Modifier.height(8.dp))
                    Text("→ Tippe oben auf „Anrufliste erlauben“.")
                }
            }

            query.isNotBlank() && !canReadContacts && !canReadCallLog -> {
                Column(Modifier.padding(14.dp)) {
                    Text("Für Namenssuche brauchst du Kontakte – für letzte Anrufe die Anrufliste.")
                    Spacer(Modifier.height(8.dp))
                    Text("→ Tippe oben auf „Kontakte erlauben“ oder „Anrufliste erlauben“.")
                }
            }

            items.isEmpty() -> {
                Column(Modifier.padding(14.dp)) {
                    Text("Keine Treffer.", style = MaterialTheme.typography.bodyMedium)
                    Text("Tipp: Tippe Name oder Nummer.", style = MaterialTheme.typography.bodySmall)
                }
            }

            else -> {
                LazyColumn {
                    itemsIndexed(items) { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 68.dp)
                                .clickable { onPick(item.number) }
                                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = CircleShape,
                                tonalElevation = 2.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(item.title.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(Modifier.weight(1f)) {
                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier.width(52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = { onCall(item.number) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Filled.Call, contentDescription = "Anrufen", modifier = Modifier.size(22.dp))
                                }
                            }
                        }

                        if (idx != items.lastIndex) {
                            HorizontalDivider(
                                Modifier,
                                DividerDefaults.Thickness,
                                DividerDefaults.color
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallButton(
    acceptImageUri: String?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 4.dp
    ) {
        if (!acceptImageUri.isNullOrBlank()) {
            AsyncImage(
                model = acceptImageUri,
                contentDescription = "Anrufen",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Call, contentDescription = "Anrufen", modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun DialPad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = { onKey(key) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(key, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = onBackspace,
                modifier = Modifier.size(56.dp),
                shape = CircleShape
            ) { Text("⌫") }

            Spacer(Modifier.width(8.dp))

            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.size(56.dp),
                shape = CircleShape
            ) { Text("C") }
        }
    }
}

private suspend fun loadSuggestions(
    context: Context,
    query: String,
    canReadCallLog: Boolean,
    canReadContacts: Boolean
): List<SuggestionItem> = withContext(Dispatchers.IO) {

    val q = query.trim()
    val out = mutableListOf<SuggestionItem>()

    if (q.isBlank()) {
        if (canReadCallLog) out += loadRecentCallsAsSuggestions(context, limit = 8)
        return@withContext out
    }

    if (canReadContacts) out += searchContactsAsSuggestions(context, q, limit = 8)
    if (canReadCallLog) out += searchCallLogAsSuggestions(context, q, limit = 8)

    out.distinctBy { normalizeNumberForCompare(it.number) }
        .take(10)
}

private fun normalizeNumberForCompare(n: String): String {
    val trimmed = n.trim()
    val hasPlus = trimmed.startsWith("+")
    val digits = trimmed.filter { it.isDigit() }
    return (if (hasPlus) "+" else "") + digits
}

private fun loadRecentCallsAsSuggestions(
    context: Context,
    limit: Int
): List<SuggestionItem> {
    val results = mutableListOf<SuggestionItem>()
    val cr = context.contentResolver

    val projection = arrayOf(
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.NUMBER
    )

    cr.query(
        CallLog.Calls.CONTENT_URI,
        projection,
        null,
        null,
        "${CallLog.Calls.DATE} DESC"
    )?.use { c ->
        while (c.moveToNext() && results.size < limit) {
            val cachedName = c.getString(0)
            val num = c.getString(1) ?: continue
            val title = cachedName?.takeIf { it.isNotBlank() } ?: num

            results.add(
                SuggestionItem(
                    title = title,
                    subtitle = num,
                    number = num,
                    source = SuggestionSource.RECENT
                )
            )
        }
    }
    return results
}

private fun searchCallLogAsSuggestions(
    context: Context,
    query: String,
    limit: Int
): List<SuggestionItem> {
    val results = mutableListOf<SuggestionItem>()
    val cr = context.contentResolver

    val projection = arrayOf(
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.NUMBER
    )

    val where = "${CallLog.Calls.CACHED_NAME} LIKE ? OR ${CallLog.Calls.NUMBER} LIKE ?"
    val args = arrayOf("%$query%", "%$query%")

    cr.query(
        CallLog.Calls.CONTENT_URI,
        projection,
        where,
        args,
        "${CallLog.Calls.DATE} DESC"
    )?.use { c ->
        while (c.moveToNext() && results.size < limit) {
            val cachedName = c.getString(0)
            val num = c.getString(1) ?: continue
            val title = cachedName?.takeIf { it.isNotBlank() } ?: num

            results.add(
                SuggestionItem(
                    title = title,
                    subtitle = "Letzter Anruf · $num",
                    number = num,
                    source = SuggestionSource.RECENT
                )
            )
        }
    }
    return results
}

private fun searchContactsAsSuggestions(
    context: Context,
    query: String,
    limit: Int
): List<SuggestionItem> {
    val results = mutableListOf<SuggestionItem>()
    val cr = context.contentResolver

    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )

    val selection =
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"

    val args = arrayOf("%$query%", "%$query%")

    cr.query(
        uri,
        projection,
        selection,
        args,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
    )?.use { c ->
        while (c.moveToNext() && results.size < limit) {
            val name = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
            val num = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue

            results.add(
                SuggestionItem(
                    title = name,
                    subtitle = num,
                    number = num,
                    source = SuggestionSource.CONTACT
                )
            )
        }
    }
    return results
}

private fun placeCall(context: Context, number: String) {
    val clean = number.trim()
    if (clean.isEmpty()) return

    val uri = "tel:$clean".toUri()
    val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    try {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        telecom.placeCall(uri, null)
    } catch (_: Throwable) {
        context.startActivity(Intent(Intent.ACTION_DIAL, uri))
    }
}
