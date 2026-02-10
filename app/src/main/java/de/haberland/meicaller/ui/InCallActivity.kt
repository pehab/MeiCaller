package de.haberland.meicaller.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import de.haberland.meicaller.data.ContactBackgroundStore
import de.haberland.meicaller.data.UiSettings
import de.haberland.meicaller.data.UiSettingsStore
import de.haberland.meicaller.telephony.CallRepo
import de.haberland.meicaller.ui.theme.MeiCallerTheme
import de.haberland.meicaller.util.normalizeForCompare
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
                accentHex = settings.accentHex,
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
    onFinish: () -> Unit,
) {
    val context = LocalContext.current

    var liveNumber by remember { mutableStateOf<String?>(null) }
    val normalized =
        remember(liveNumber) {
            liveNumber?.takeIf { it.isNotBlank() }?.let { normalizeForCompare(it) }.orEmpty()
        }

    val contactBgUri by if (normalized.isNotBlank()) {
        ContactBackgroundStore.backgroundUriFlow(context, normalized).collectAsState(initial = null)
    } else {
        remember { mutableStateOf(null) }
    }

    val effectiveBg =
        remember(contactBgUri, settings.backgroundUri) {
            contactBgUri?.takeIf { it.isNotBlank() } ?: settings.backgroundUri
        }

    Box(Modifier.fillMaxSize()) {
        if (!effectiveBg.isNullOrBlank()) {
            AsyncImage(
                model = effectiveBg,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
            ) {}
        }

        InCallScreen(
            acceptUri = settings.acceptButtonUri,
            rejectUri = settings.rejectButtonUri,
            onFinish = onFinish,
            onNumberObserved = { n -> liveNumber = n },
            modifier =
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InCallScreen(
    acceptUri: String?,
    rejectUri: String?,
    onFinish: () -> Unit,
    onNumberObserved: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val call = remember { CallRepo.call() }
    val service = remember { CallRepo.service() }

    // Audio state from repo (modern on 34+, legacy fallback below 34)
    val isMuted by CallRepo.isMuted.collectAsState()
    val currentEndpoint by CallRepo.currentEndpoint.collectAsState()
    val legacyRoute by CallRepo.legacyAudioRoute.collectAsState()

    val isSpeaker =
        if (Build.VERSION.SDK_INT >= 34) {
            currentEndpoint?.endpointType == CallEndpoint.TYPE_SPEAKER
        } else {
            legacyRoute == CallAudioState.ROUTE_SPEAKER
        }

    if (call == null || service == null) {
        Column(modifier) {
            Text("Kein aktiver Call gefunden.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onFinish) { Text("Schließen") }
        }
        return
    }

    // Keep initial state without touching deprecated audio APIs
    var callState by remember {
        mutableIntStateOf(call.details?.state ?: Call.STATE_NEW)
    }

    // Caller info
    var displayName by remember { mutableStateOf<String?>(null) }
    var number by remember { mutableStateOf<String?>(null) }
    var systemName by remember { mutableStateOf<String?>(null) }

    // Contact bits
    var contactPhotoUri by remember { mutableStateOf<String?>(null) }
    var contactLabel by remember { mutableStateOf<String?>(null) }

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
        return details?.callerDisplayName?.takeIf { it.isNotBlank() }
    }

    suspend fun updateCallerInfoAsync() {
        val num = extractNumberFromCall()
        number = num
        onNumberObserved(num)

        systemName = extractSystemName()

        val canReadContacts =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                    PackageManager.PERMISSION_GRANTED

        if (!systemName.isNullOrBlank()) {
            displayName = systemName
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

    // initial caller info
    LaunchedEffect(call) {
        updateCallerInfoAsync()
        // refresh audio state (repo decides modern/legacy)
        CallRepo.refreshFromService(service)
    }

    // state changes
    DisposableEffect(call) {
        val callback =
            object : Call.Callback() {
                override fun onStateChanged(call: Call, ignored: Int) {
                    // use non-deprecated source of truth
                    callState = call.details?.state ?: callState
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

    val statusText =
        when (callState) {
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

    if (showDtmf && isActiveLike) {
        ModalBottomSheet(
            onDismissRequest = { showDtmf = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            DtmfPad(
                onTone = { ch ->
                    try {
                        call.playDtmfTone(ch)
                    } catch (_: Throwable) {
                    }
                },
                onStop = {
                    try {
                        call.stopDtmfTone()
                    } catch (_: Throwable) {
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
            )
            Spacer(Modifier.height(14.dp))
        }
    }

    Column(modifier = modifier) {
        Column(Modifier.fillMaxWidth()) {
            Text(statusText, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))

            CallerHeader(
                name = displayName ?: (number ?: "Unbekannt"),
                number = number,
                photoUri = contactPhotoUri,
                subtitle = contactLabel,
            )
        }

        Spacer(Modifier.weight(1f))

        when {
            callState == Call.STATE_RINGING -> {
                IncomingControlsBottom(
                    acceptUri = acceptUri,
                    rejectUri = rejectUri,
                    onAnswer = { call.answer(VideoProfile.STATE_AUDIO_ONLY) },
                    onReject = {
                        call.disconnect()
                        onFinish()
                    },
                )
            }

            isActiveLike -> {
                ActiveControlsBottom(
                    rejectUri = rejectUri,
                    isMuted = isMuted,
                    isSpeaker = isSpeaker,
                    onToggleMute = {
                        val newVal = !isMuted
                        CallRepo.setMuted(service, newVal)
                    },
                    onToggleSpeaker = {
                        CallRepo.toggleSpeaker(context, service)
                    },
                    onShowDialpad = { showDtmf = true },
                    onHangup = {
                        call.disconnect()
                        onFinish()
                    },
                )
            }

            else -> {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
    subtitle: String?,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(78.dp),
            shape = CircleShape,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (!photoUri.isNullOrBlank()) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Kontaktbild",
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
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
                overflow = TextOverflow.Ellipsis,
            )

            val sub =
                buildString {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IncomingControlsBottom(
    acceptUri: String?,
    rejectUri: String?,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlatRoundImageButton(
            label = "Ablehnen",
            imageUri = rejectUri,
            fallbackText = "❌",
            onClick = onReject,
            modifier = Modifier.weight(1f),
            sizeDp = 112,
        )
        FlatRoundImageButton(
            label = "Annehmen",
            imageUri = acceptUri,
            fallbackText = "✅",
            onClick = onAnswer,
            modifier = Modifier.weight(1f),
            sizeDp = 112,
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
    onHangup: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconTogglePill(
                label = if (isMuted) "Unmute" else "Mute",
                checked = isMuted,
                onClick = onToggleMute,
                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                modifier = Modifier.weight(1f),
            )

            IconTogglePill(
                label = if (isSpeaker) "Speaker aus" else "Speaker an",
                checked = isSpeaker,
                onClick = onToggleSpeaker,
                icon =
                    if (isSpeaker) Icons.AutoMirrored.Filled.VolumeUp
                    else Icons.AutoMirrored.Filled.VolumeOff,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onShowDialpad,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Tastenfeld")
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            FlatRoundImageButton(
                label = "Auflegen",
                imageUri = rejectUri,
                fallbackText = "❌",
                onClick = onHangup,
                modifier = Modifier,
                sizeDp = 96,
                showLabel = false,
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
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick),
        tonalElevation = if (checked) 6.dp else 2.dp,
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
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
    showLabel: Boolean = true,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier =
                Modifier
                    .size(sizeDp.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
            shape = CircleShape,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.primary,
        ) {
            if (!imageUri.isNullOrBlank()) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
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
    modifier: Modifier = Modifier,
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

        val rows =
            listOf(
                listOf('1', '2', '3'),
                listOf('4', '5', '6'),
                listOf('7', '8', '9'),
                listOf('*', '0', '#'),
            )

        rows.forEach { r ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                r.forEach { ch ->
                    OutlinedButton(
                        onClick = { press(ch) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(64.dp),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(0.dp),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class ContactInfo(
    val name: String?,
    val photoUri: String?,
    val label: String?,
)

private suspend fun lookupContactInfoByNumber(
    context: Context,
    rawNumber: String,
): ContactInfo =
    withContext(Dispatchers.IO) {
        val lookupUri =
            Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(rawNumber),
            )

        val lookupProjection =
            arrayOf(
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
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

        var label: String? = null
        if (contactId != null) {
            val phoneProjection =
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                )

            val sel = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?"
            val args = arrayOf(contactId.toString())

            val callCandidates = buildCompareCandidates(rawNumber)

            var fallbackLabel: String? = null

            context.contentResolver
                .query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    phoneProjection,
                    sel,
                    args,
                    null,
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

            if (label.isNullOrBlank()) label = fallbackLabel
        }

        ContactInfo(name = name, photoUri = photo, label = label)
    }

private fun buildCompareCandidates(raw: String): Set<String> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptySet()

    val digitsOnly = trimmed.filter { it.isDigit() }
    val candidates = linkedSetOf<String>()

    if (digitsOnly.isNotBlank()) candidates.add(digitsOnly)

    val norm = normalizeForCompare(trimmed)
    if (norm.isNotBlank()) {
        val normDigits = norm.filter { it.isDigit() }
        if (normDigits.isNotBlank()) candidates.add(normDigits)

        if (normDigits.startsWith("49") && normDigits.length > 2) {
            candidates.add("0" + normDigits.drop(2))
            candidates.add(normDigits.drop(2))
        }
    }

    if (digitsOnly.startsWith("0") && digitsOnly.length >= 6) {
        val noTrunk = digitsOnly.drop(1)
        candidates.add(noTrunk)
        candidates.add("49$noTrunk")
    }

    if (!digitsOnly.startsWith("0") && digitsOnly.length in 6..15) {
        candidates.add("0$digitsOnly")
        candidates.add("49$digitsOnly")
    }

    return candidates
}

private fun toHumanPhoneLabel(
    type: Int,
    customLabel: String?,
): String? =
    when (type) {
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
