package de.haberland.meicaller.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import de.haberland.meicaller.data.UiSettings
import de.haberland.meicaller.data.UiSettingsStore
import de.haberland.meicaller.telephony.CallRepo
import de.haberland.meicaller.ui.theme.MeiCallerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        setContent {
            val settings by UiSettingsStore.flow(this).collectAsState(initial = UiSettings())

            MeiCallerTheme(
                primaryHex = settings.primaryHex,
                accentHex = settings.accentHex
            ) {
                Surface(Modifier.fillMaxSize()) {
                    InCallRoot(settings = settings, onFinish = { finishAndRemoveTask() })
                }
            }
        }
    }
}

@Composable
private fun InCallRoot(
    settings: UiSettings,
    onFinish: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {

        if (!settings.backgroundUri.isNullOrBlank()) {
            AsyncImage(
                model = settings.backgroundUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f)
            ) {}
        }

        InCallScreen(
            acceptUri = settings.acceptButtonUri,
            rejectUri = settings.rejectButtonUri,
            onFinish = onFinish,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InCallScreen(
    acceptUri: String?,
    rejectUri: String?,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val call = remember { CallRepo.call() }
    val service = remember { CallRepo.service() }

    if (call == null || service == null) {
        Column(modifier) {
            Text("Kein aktiver Call gefunden.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onFinish) { Text("Schließen") }
        }
        return
    }

    var callState by remember { mutableIntStateOf(call.state) }
    var isMuted by remember { mutableStateOf(false) }
    var audioRoute by remember { mutableIntStateOf(CallAudioState.ROUTE_EARPIECE) }

    // Caller info
    var displayName by remember { mutableStateOf<String?>(null) }
    var number by remember { mutableStateOf<String?>(null) }
    var systemName by remember { mutableStateOf<String?>(null) }

    // Contact bits
    var contactPhotoUri by remember { mutableStateOf<String?>(null) }
    var contactLabel by remember { mutableStateOf<String?>(null) } // Mobil/Privat/Arbeit etc.

    // DTMF bottom sheet
    var showDtmf by remember { mutableStateOf(false) }

    fun extractNumberFromCall(): String? {
        val details = call.details
        val h: Uri? = details?.handle
        return when {
            h == null -> null
            h.scheme == "tel" -> h.schemeSpecificPart
            else -> h.toString()
        }
    }

    fun extractSystemName(): String? {
        val details = call.details
        return details?.callerDisplayName?.toString()?.takeIf { it.isNotBlank() }
    }

    suspend fun updateCallerInfoAsync() {
        val num = extractNumberFromCall()
        number = num
        systemName = extractSystemName()

        val canReadContacts =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                    PackageManager.PERMISSION_GRANTED

        // Wenn System bereits einen Namen liefert, nehmen wir den sofort
        if (!systemName.isNullOrBlank()) {
            displayName = systemName

            // trotzdem Foto/Label versuchen (über Nummer matchen)
            if (canReadContacts && !num.isNullOrBlank()) {
                val info = lookupContactInfoByNumber(context, num)
                contactPhotoUri = info.photoUri
                contactLabel = info.label
            } else {
                contactPhotoUri = null
                contactLabel = null
            }
            return
        }

        // Sonst: Lookup über Kontakte
        if (canReadContacts && !num.isNullOrBlank()) {
            val info = lookupContactInfoByNumber(context, num)
            displayName = info.name ?: num
            contactPhotoUri = info.photoUri
            contactLabel = info.label
        } else {
            displayName = num
            contactPhotoUri = null
            contactLabel = null
        }
    }

    // audio state initial
    LaunchedEffect(service) {
        val cas = service.callAudioState
        isMuted = cas?.isMuted == true
        audioRoute = cas?.route ?: CallAudioState.ROUTE_EARPIECE
    }

    // initial caller info
    LaunchedEffect(call) {
        updateCallerInfoAsync()
    }

    // state changes
    DisposableEffect(call) {
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                callState = state
            }
        }
        call.registerCallback(callback)
        onDispose { call.unregisterCallback(callback) }
    }

    // refresh info on state change
    LaunchedEffect(callState) {
        updateCallerInfoAsync()
        if (callState == Call.STATE_DISCONNECTED) onFinish()
    }

    val statusText = when (callState) {
        Call.STATE_RINGING -> "Eingehender Anruf"
        Call.STATE_DIALING -> "Wähle…"
        Call.STATE_CONNECTING -> "Verbinde…"
        Call.STATE_ACTIVE -> "Im Gespräch"
        Call.STATE_HOLDING -> "Gehalten"
        Call.STATE_DISCONNECTED -> "Beendet"
        else -> "Telefon"
    }

    val isActiveLike =
        callState == Call.STATE_ACTIVE ||
                callState == Call.STATE_DIALING ||
                callState == Call.STATE_CONNECTING ||
                callState == Call.STATE_HOLDING

    // DTMF BottomSheet
    if (showDtmf && isActiveLike) {
        ModalBottomSheet(
            onDismissRequest = { showDtmf = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            DtmfPad(
                onTone = { ch ->
                    try { call.playDtmfTone(ch) } catch (_: Throwable) {}
                },
                onStop = {
                    try { call.stopDtmfTone() } catch (_: Throwable) {}
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .navigationBarsPadding()
            )
            Spacer(Modifier.height(14.dp))
        }
    }

    Column(modifier = modifier) {

        // TOP
        Column(Modifier.fillMaxWidth()) {
            Text(statusText, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))

            CallerHeader(
                name = displayName ?: (number ?: "Unbekannt"),
                number = number,
                photoUri = contactPhotoUri,
                subtitle = contactLabel
            )
        }

        Spacer(Modifier.weight(1f))

        // BOTTOM: Controls
        when {
            callState == Call.STATE_RINGING -> {
                IncomingControlsBottom(
                    acceptUri = acceptUri,
                    rejectUri = rejectUri,
                    onAnswer = { call.answer(VideoProfile.STATE_AUDIO_ONLY) },
                    onReject = { call.disconnect(); onFinish() }
                )
            }

            isActiveLike -> {
                ActiveControlsBottom(
                    rejectUri = rejectUri,
                    isMuted = isMuted,
                    isSpeaker = audioRoute == CallAudioState.ROUTE_SPEAKER,
                    onToggleMute = {
                        val newVal = !isMuted
                        service.setMuted(newVal)
                        isMuted = newVal
                    },
                    onToggleSpeaker = {
                        val newRoute =
                            if (audioRoute == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_EARPIECE
                            else CallAudioState.ROUTE_SPEAKER
                        service.setAudioRoute(newRoute)
                        audioRoute = newRoute
                    },
                    onShowDialpad = { showDtmf = true },
                    onHangup = { call.disconnect(); onFinish() }
                )
            }

            else -> {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Schließen") }
            }
        }
    }
}

@Composable
private fun CallerHeader(
    name: String,
    number: String?,
    photoUri: String?,
    subtitle: String?
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(78.dp),
            shape = CircleShape,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (!photoUri.isNullOrBlank()) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Kontaktbild",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val sub = buildString {
                if (!subtitle.isNullOrBlank()) append(subtitle)
                if (!number.isNullOrBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(number)
                }
            }.ifBlank { "Unbekannt" }

            Spacer(Modifier.height(4.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IncomingControlsBottom(
    acceptUri: String?,
    rejectUri: String?,
    onAnswer: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlatRoundImageButton(
            label = "Ablehnen",
            imageUri = rejectUri,
            fallbackText = "❌",
            onClick = onReject,
            modifier = Modifier.weight(1f),
            sizeDp = 112
        )
        FlatRoundImageButton(
            label = "Annehmen",
            imageUri = acceptUri,
            fallbackText = "✅",
            onClick = onAnswer,
            modifier = Modifier.weight(1f),
            sizeDp = 112
        )
    }
}

@Composable
private fun ActiveControlsBottom(
    rejectUri: String?,
    isMuted: Boolean,
    isSpeaker: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onShowDialpad: () -> Unit,
    onHangup: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconTogglePill(
                label = if (isMuted) "Unmute" else "Mute",
                checked = isMuted,
                onClick = onToggleMute,
                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                modifier = Modifier.weight(1f)
            )

            IconTogglePill(
                label = if (isSpeaker) "Speaker aus" else "Speaker an",
                checked = isSpeaker,
                onClick = onToggleSpeaker,
                icon = if (isSpeaker) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onShowDialpad,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Tastenfeld")
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            FlatRoundImageButton(
                label = "Auflegen",
                imageUri = rejectUri,
                fallbackText = "❌",
                onClick = onHangup,
                modifier = Modifier,
                sizeDp = 96,
                showLabel = false
            )
        }
    }
}

@Composable
private fun IconTogglePill(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        tonalElevation = if (checked) 6.dp else 2.dp,
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label)
            Spacer(Modifier.width(10.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FlatRoundImageButton(
    label: String,
    imageUri: String?,
    fallbackText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 92,
    showLabel: Boolean = true
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {

        Surface(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            shape = CircleShape,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.primary
        ) {
            if (!imageUri.isNullOrBlank()) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(fallbackText, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }

        if (showLabel) {
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DtmfPad(
    onTone: (Char) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    fun press(ch: Char) {
        scope.launch {
            onTone(ch)
            delay(140)
            onStop()
        }
    }

    Column(modifier) {
        Text("Tastenfeld", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))

        val rows = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
            listOf('*', '0', '#')
        )

        rows.forEach { r ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                r.forEach { ch ->
                    OutlinedButton(
                        onClick = { press(ch) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(ch.toString(), style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Tipp: Für IVR-Menüs einfach tippen (Ton wird kurz gesendet).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Kontaktinfo wie im Dialer: Name + Foto + Label der passenden Nummer (Mobil/Privat/Arbeit) */
private data class ContactInfo(
    val name: String?,
    val photoUri: String?,
    val label: String?
)

private suspend fun lookupContactInfoByNumber(context: Context, rawNumber: String): ContactInfo {
    return withContext(Dispatchers.IO) {
        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(rawNumber)
        )

        // 1) PhoneLookup -> contactId + name + photo
        val lookupProjection = arrayOf(
            ContactsContract.PhoneLookup._ID,
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_URI,
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
        )

        var contactId: Long? = null
        var name: String? = null
        var photo: String? = null

        context.contentResolver.query(lookupUri, lookupProjection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                contactId = c.getLong(0)
                name = c.getString(1)?.takeIf { it.isNotBlank() }
                val full = c.getString(2)?.takeIf { it.isNotBlank() }
                val thumb = c.getString(3)?.takeIf { it.isNotBlank() }
                photo = full ?: thumb
            }
        }

        // 2) Passende Nummer im Kontakt suchen und deren TYPE/LABEL nehmen
        var label: String? = null
        if (contactId != null) {
            val phoneProjection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            val sel = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?"
            val args = arrayOf(contactId.toString())

            val callCandidates = buildCompareCandidates(rawNumber)

            var fallbackLabel: String? = null

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                phoneProjection,
                sel,
                args,
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(0)
                    val custom = c.getString(1)
                    val contactNum = c.getString(2)

                    val thisLabel = toHumanPhoneLabel(type, custom)
                    if (fallbackLabel.isNullOrBlank()) fallbackLabel = thisLabel

                    if (!contactNum.isNullOrBlank()) {
                        val contactCandidates = buildCompareCandidates(contactNum)
                        if (callCandidates.any { it in contactCandidates }) {
                            label = thisLabel
                            break
                        }
                    }
                }
            }

            // Wenn kein Match (z.B. weil Nummern extrem unterschiedlich formatiert), nimm wenigstens eine sinnvolle
            if (label.isNullOrBlank()) label = fallbackLabel
        }

        ContactInfo(name = name, photoUri = photo, label = label)
    }
}

/**
 * Baut Vergleichsvarianten einer Nummer:
 * - nur Ziffern (ohne +)
 * - E164-nahe Variante für DE (+49 / 0049 / 0…)
 * - lokale Variante ohne Landesvorwahl
 *
 * Ziel: 0176… soll +49176… matchen etc.
 */
private fun buildCompareCandidates(raw: String): Set<String> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptySet()

    // normalisierte "digits only"
    val digitsOnly = trimmed.filter { it.isDigit() }

    // plus/e164-artig erkennen
    val hasPlus = trimmed.trimStart().startsWith("+")
    val starts00 = trimmed.trimStart().startsWith("00")

    val candidates = linkedSetOf<String>()

    // Grundkandidat: digits only
    if (digitsOnly.isNotBlank()) candidates.add(digitsOnly)

    // Wenn + oder 00 vorhanden, versuchen wir Landesvorwahl abzuleiten
    // z.B. +49176... -> digitsOnly "49176..."
    // z.B. 0049176... -> digitsOnly "49176..."
    val e164Digits = when {
        hasPlus -> digitsOnly
        starts00 && digitsOnly.length > 2 -> digitsOnly.drop(2) // 00 + CC...
        else -> null
    }
    if (!e164Digits.isNullOrBlank()) {
        candidates.add(e164Digits)
        // lokale Variante ohne DE-CC (49)
        if (e164Digits.startsWith("49") && e164Digits.length > 2) {
            candidates.add("0" + e164Digits.drop(2)) // 0176... als Vergleich
            candidates.add(e164Digits.drop(2))       // 176...
        }
    }

    // Wenn es lokal aussieht (0...) und DE wahrscheinlich:
    // 0176... -> 49176... (E164 digits ohne +)
    if (digitsOnly.startsWith("0") && digitsOnly.length >= 6) {
        val noTrunk = digitsOnly.drop(1)
        candidates.add(noTrunk) // 176...
        candidates.add("49$noTrunk") // 49176...
    }

    // Einige speichern "176..." ohne führende 0
    // -> ergänze 0 + number
    if (!digitsOnly.startsWith("0") && digitsOnly.length in 6..15) {
        candidates.add("0$digitsOnly")
        // und DE-CC falls plausibel
        candidates.add("49$digitsOnly")
    }

    return candidates
}

private fun toHumanPhoneLabel(type: Int, customLabel: String?): String? {
    return when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobil"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Privat"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Arbeit"
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Hauptnummer"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "Fax"
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "Weitere"
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM ->
            customLabel?.takeIf { it.isNotBlank() } ?: "Custom"
        else -> null
    }
}
