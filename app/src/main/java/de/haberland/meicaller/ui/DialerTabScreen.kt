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
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Screen displaying a dialer interface with a dial pad and search suggestions.
 * It allows users to type a number or name and search through contacts and call logs.
 * @param settings Current UI settings for colors and button images.
 * @param initialNumber Optional initial phone number to populate the dialer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerTabScreen(
    settings: UiSettings,
    initialNumber: String = "",
) {
    val context = LocalContext.current
    var number by remember { mutableStateOf(initialNumber) }
    var pendingCall by remember { mutableStateOf<String?>(null) }

    // Permission request launchers
    val requestCallPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val n = pendingCall
            if (granted && n != null) placeCall(context, n)
        }

    val requestCallLogPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* UI updates */ }

    val requestContactsPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* UI updates */ }

    /** Initiates a call or requests permission if not already granted. */
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

    // Dynamically load suggestions based on the current input and available permissions
    val suggestions by produceState(
        initialValue = emptyList(),
        number,
        hasCallLogPermission,
        hasContactsPermission,
    ) {
        value =
            loadSuggestions(
                context = context,
                query = number,
                canReadCallLog = hasCallLogPermission,
                canReadContacts = hasContactsPermission,
            )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
    ) {
        // --- FIXED TOP SECTION: SUGGESTIONS ---
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (number.isBlank()) "Vorgeschlagen" else "Treffer",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )

            // Show permission buttons if access is missing
            when {
                !hasCallLogPermission ->
                    TextButton(onClick = { requestCallLogPermission.launch(Manifest.permission.READ_CALL_LOG) }) {
                        Text("Anrufliste erlauben")
                    }
                !hasContactsPermission ->
                    TextButton(onClick = { requestContactsPermission.launch(Manifest.permission.READ_CONTACTS) }) {
                        Text("Kontakte erlauben")
                    }
            }
        }

        // The suggestions list takes available space but doesn't push the bottom section
        Box(modifier = Modifier.weight(1f)) {
            SuggestionsCard(
                canReadCallLog = hasCallLogPermission,
                canReadContacts = hasContactsPermission,
                query = number,
                items = suggestions,
                onPick = { picked -> number = picked },
                onCall = { picked -> callOrAskPermission(picked) },
            )
        }

        // --- FIXED BOTTOM SECTION: INPUT & DIAL PAD ---
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .navigationBarsPadding(),
        ) {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("Nummer oder Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            DialPad(
                onKey = { number += it },
                onBackspace = { if (number.isNotEmpty()) number = number.dropLast(1) },
                onClear = { number = "" },
            )

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CallButton(
                    acceptImageUri = settings.acceptButtonUri,
                    onClick = { callOrAskPermission(number) },
                )
            }
        }
    }
}

private enum class SuggestionSource { RECENT, CONTACT }

/**
 * Data class for a single search suggestion result.
 */
private data class SuggestionItem(
    val title: String,
    val subtitle: String,
    val number: String,
    val source: SuggestionSource,
)

/**
 * Displays a list of suggested contacts or recent calls in a card.
 */
@Composable
private fun SuggestionsCard(
    canReadCallLog: Boolean,
    canReadContacts: Boolean,
    query: String,
    items: List<SuggestionItem>,
    onPick: (String) -> Unit,
    onCall: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp), // Ensure some minimal height so it doesn't collapse
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
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Keine Treffer.", style = MaterialTheme.typography.bodyMedium)
                    Text("Tipp: Tippe Name oder Nummer.", style = MaterialTheme.typography.bodySmall)
                }
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(items) { idx, item ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 68.dp)
                                    .clickable { onPick(item.number) }
                                    .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = CircleShape,
                                tonalElevation = 2.dp,
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
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Box(
                                modifier = Modifier.width(52.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                IconButton(
                                    onClick = { onCall(item.number) },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(Icons.Filled.Call, contentDescription = "Anrufen", modifier = Modifier.size(22.dp))
                                }
                            }
                        }

                        if (idx != items.lastIndex) {
                            HorizontalDivider(
                                Modifier,
                                DividerDefaults.Thickness,
                                DividerDefaults.color,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Specialized button to initiate a call, supporting a custom image.
 */
@Composable
private fun CallButton(
    acceptImageUri: String?,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 4.dp,
    ) {
        if (!acceptImageUri.isNullOrBlank()) {
            AsyncImage(
                model = acceptImageUri,
                contentDescription = "Anrufen",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Call, contentDescription = "Anrufen", modifier = Modifier.size(36.dp))
            }
        }
    }
}

/**
 * Standard dial pad UI component with numeric keys and backspace/clear buttons.
 */
@Composable
private fun DialPad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
) {
    val rows =
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#"),
        )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = { onKey(key) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(64.dp),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(key, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = onBackspace,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
            ) { Text("⌫") }

            Spacer(Modifier.width(8.dp))

            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
            ) { Text("C") }
        }
    }
}

/**
 * Loads suggestions based on input query from contacts and call logs.
 */
private suspend fun loadSuggestions(
    context: Context,
    query: String,
    canReadCallLog: Boolean,
    canReadContacts: Boolean,
): List<SuggestionItem> =
    withContext(Dispatchers.IO) {
        val q = query.trim()
        val out = mutableListOf<SuggestionItem>()

        if (q.isBlank()) {
            if (canReadCallLog) out += loadRecentCallsAsSuggestions(context)
            return@withContext out
        }

        if (canReadContacts) out += searchContactsAsSuggestions(context, q)
        if (canReadCallLog) out += searchCallLogAsSuggestions(context, q)

        out
            .distinctBy { normalizeNumberForCompare(it.number) }
            .take(10)
    }

/** Simplifies a number for comparison (digits and optional plus). */
private fun normalizeNumberForCompare(n: String): String {
    val trimmed = n.trim()
    val hasPlus = trimmed.startsWith("+")
    val digits = trimmed.filter { it.isDigit() }
    return (if (hasPlus) "+" else "") + digits
}

/** Fetches recent calls from the call log provider. */
private fun loadRecentCallsAsSuggestions(
    context: Context,
    limit: Int = 8,
): List<SuggestionItem> {
    val results = mutableListOf<SuggestionItem>()
    val cr = context.contentResolver

    val projection =
        arrayOf(
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
        )

    cr
        .query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC",
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
                        source = SuggestionSource.RECENT,
                    ),
                )
            }
        }
    return results
}

/** Searches the call log for entries matching the query. */
private fun searchCallLogAsSuggestions(
    context: Context,
    query: String,
    limit: Int = 8,
): List<SuggestionItem> {
    val results = mutableListOf<SuggestionItem>()
    val cr = context.contentResolver

    val projection =
        arrayOf(
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
        )

    val where = "${CallLog.Calls.CACHED_NAME} LIKE ? OR ${CallLog.Calls.NUMBER} LIKE ?"
    val args = arrayOf("%$query%", "%$query%")

    cr
        .query(
            CallLog.Calls.CONTENT_URI,
            projection,
            where,
            args,
            "${CallLog.Calls.DATE} DESC",
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
                        source = SuggestionSource.RECENT,
                    ),
                )
            }
        }
    return results
}

/** Searches the system contacts for entries matching the query. */
private fun searchContactsAsSuggestions(
    context: Context,
    query: String,
    limit: Int = 8,
): List<SuggestionItem> {
    val results = mutableListOf<SuggestionItem>()
    val cr = context.contentResolver

    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val projection =
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )

    val selection =
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"

    val args = arrayOf("%$query%", "%$query%")

    cr
        .query(
            uri,
            projection,
            selection,
            args,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
        )?.use { c ->
            while (c.moveToNext() && results.size < limit) {
                val name = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                val num = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue

                results.add(
                    SuggestionItem(
                        title = name,
                        subtitle = num,
                        number = num,
                        source = SuggestionSource.CONTACT,
                    ),
                )
            }
        }
    return results
}

/**
 * Initiates a phone call or falls back to the system dialer if permissions are missing.
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
            return
        }
        telecom.placeCall(uri, null)
    } catch (_: Throwable) {
        context.startActivity(Intent(Intent.ACTION_DIAL, uri))
    }
}
