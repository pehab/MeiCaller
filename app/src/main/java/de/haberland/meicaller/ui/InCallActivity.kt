package de.haberland.meicaller.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
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
    private var proximityWakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "MeiCaller:ProximityWakeLock"
            )
        }

        setContent {
            val settings by UiSettingsStore.flow(this).collectAsState(initial = UiSettings())
            MeiCallerTheme(primaryHex = settings.primaryHex, accentHex = settings.accentHex) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) {
                    InCallRoot(
                        settings = settings,
                        onFinish = { finishAndRemoveTask() },
                        onCallStateChanged = { handleProximitySensor(it) }
                    )
                }
            }
        }
    }

    private fun handleProximitySensor(state: Int) {
        val wakeLock = proximityWakeLock ?: return
        val active = state == Call.STATE_ACTIVE || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING
        if (active && !wakeLock.isHeld) {
            try { wakeLock.acquire(10 * 60 * 1000L) } catch (_: Exception) {}
        } else if (!active && wakeLock.isHeld) {
            try { wakeLock.release() } catch (_: Exception) {}
        }
    }

    override fun onPause() {
        super.onPause()
        if (proximityWakeLock?.isHeld == true) {
            try { proximityWakeLock?.release() } catch (_: Exception) {}
        }
    }
}

@Composable
private fun InCallRoot(settings: UiSettings, onFinish: () -> Unit, onCallStateChanged: (Int) -> Unit) {
    val context = LocalContext.current
    var liveNumber by remember { mutableStateOf<String?>(null) }
    val normalized = remember(liveNumber) { liveNumber?.let { normalizeForCompare(it) }.orEmpty() }

    val contactBgUri by if (normalized.isNotBlank()) {
        ContactBackgroundStore.backgroundUriFlow(context, normalized).collectAsState(initial = null)
    } else remember { mutableStateOf(null) }

    val effectiveBg = contactBgUri?.takeIf { it.isNotBlank() } ?: settings.backgroundUri

    Box(Modifier.fillMaxSize()) {
        if (!effectiveBg.isNullOrBlank()) {
            AsyncImage(
                model = effectiveBg,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(12.dp),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
        }

        InCallScreen(
            settings = settings,
            onFinish = onFinish,
            onNumberObserved = { liveNumber = it },
            onCallStateChanged = onCallStateChanged,
            modifier = Modifier.fillMaxSize().systemBarsPadding().navigationBarsPadding().padding(24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InCallScreen(
    settings: UiSettings,
    onFinish: () -> Unit,
    onNumberObserved: (String?) -> Unit,
    onCallStateChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val call = remember { CallRepo.call() }
    val service = remember { CallRepo.service() }
    val isMuted by CallRepo.isMuted.collectAsState()
    val currentEndpoint by CallRepo.currentEndpoint.collectAsState()
    val legacyRoute by CallRepo.legacyAudioRoute.collectAsState()

    val isSpeaker = if (Build.VERSION.SDK_INT >= 34) currentEndpoint?.endpointType == CallEndpoint.TYPE_SPEAKER
                    else legacyRoute == CallAudioState.ROUTE_SPEAKER

    if (call == null || service == null) {
        onFinish()
        return
    }

    var callState by remember { mutableIntStateOf(call.details?.state ?: Call.STATE_NEW) }
    var displayName by remember { mutableStateOf<String?>(null) }
    var number by remember { mutableStateOf<String?>(null) }
    var contactPhotoUri by remember { mutableStateOf<String?>(null) }
    var showDtmf by remember { mutableStateOf(false) }
    var durationSeconds by remember { mutableLongStateOf(0L) }

    val accentColor = remember(settings.accentHex) {
        try { Color(settings.accentHex.toColorInt()) }
        catch (_: Exception) { Color(0xFF7C4DFF) }
    }

    LaunchedEffect(call) {
        val presentation = call.details?.handlePresentation ?: TelecomManager.PRESENTATION_ALLOWED
        val rawNum = call.details?.handle?.schemeSpecificPart?.trim() ?: ""

        if (presentation != TelecomManager.PRESENTATION_ALLOWED) {
            number = null
            displayName = when (presentation) {
                TelecomManager.PRESENTATION_RESTRICTED -> "Private Nummer"
                TelecomManager.PRESENTATION_PAYPHONE -> "Öffentliches Telefon"
                else -> "Unbekannt"
            }
        } else {
            number = rawNum
            onNumberObserved(rawNum)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                val info = lookupContactInfoByNumber(context, rawNum)
                displayName = info.name
                contactPhotoUri = info.photoUri
            }
        }
        CallRepo.refreshFromService(service)
    }

    LaunchedEffect(callState) {
        if (callState == Call.STATE_ACTIVE) {
            val start = System.currentTimeMillis() - (durationSeconds * 1000)
            while (callState == Call.STATE_ACTIVE) {
                durationSeconds = (System.currentTimeMillis() - start) / 1000
                delay(1000)
            }
        }
    }

    DisposableEffect(call) {
        val cb = object : Call.Callback() {
            override fun onStateChanged(c: Call, s: Int) { callState = c.details?.state ?: s }
        }
        call.registerCallback(cb)
        onDispose { call.unregisterCallback(cb) }
    }

    LaunchedEffect(callState) {
        onCallStateChanged(callState)
        if (callState == Call.STATE_DISCONNECTED) {
            delay(1200)
            onFinish()
        }
    }

    if (showDtmf) {
        ModalBottomSheet(onDismissRequest = { showDtmf = false }) {
            DtmfPad(onTone = { call.playDtmfTone(it) }, onStop = { call.stopDtmfTone() })
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        
        // Caller Info Section
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(140.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)) {
                if (!contactPhotoUri.isNullOrBlank()) {
                    AsyncImage(model = contactPhotoUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = (displayName ?: number ?: "?").take(1).uppercase(), style = MaterialTheme.typography.displayLarge)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Text(displayName ?: number ?: "Unbekannt", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (!number.isNullOrBlank()) {
                Text(number ?: "", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Spacer(Modifier.height(12.dp))
            
            AnimatedContent(targetState = callState, label = "Status", transitionSpec = { fadeIn() togetherWith fadeOut() }) { state ->
                val (txt, color) = when (state) {
                    Call.STATE_RINGING -> "Eingehender Anruf..." to MaterialTheme.colorScheme.primary
                    Call.STATE_DIALING -> "Wähle..." to MaterialTheme.colorScheme.primary
                    Call.STATE_CONNECTING -> "Verbinde..." to MaterialTheme.colorScheme.primary
                    Call.STATE_ACTIVE -> formatDuration(durationSeconds) to MaterialTheme.colorScheme.primary
                    Call.STATE_HOLDING -> "Gehalten" to MaterialTheme.colorScheme.secondary
                    Call.STATE_DISCONNECTED -> "Beendet" to MaterialTheme.colorScheme.error
                    else -> "" to MaterialTheme.colorScheme.onSurface
                }
                Text(txt, style = MaterialTheme.typography.headlineSmall, color = color)
            }
        }

        Spacer(Modifier.weight(1f))

        // Controls Section
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            when (callState) {
                Call.STATE_RINGING -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally)) {
                        CallActionButton(
                            imageUri = settings.rejectButtonUri, 
                            icon = Icons.Filled.CallEnd, 
                            color = Color(0xFFF44336), 
                            label = "Ablehnen", 
                            size = 112, 
                            onClick = { call.disconnect() }
                        )
                        CallActionButton(
                            imageUri = settings.acceptButtonUri, 
                            icon = Icons.Filled.Mic, 
                            color = Color(0xFF4CAF50), 
                            label = "Annehmen", 
                            size = 112, 
                            onClick = { call.answer(VideoProfile.STATE_AUDIO_ONLY) }
                        )
                    }
                }
                Call.STATE_DISCONNECTED -> {
                    // Controls ausblenden bei Disconnect
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)) {
                            InCallToggle(
                                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic, 
                                active = isMuted, 
                                accentColor = accentColor,
                                onClick = { CallRepo.setMuted(service, !isMuted) }
                            )
                            InCallToggle(
                                icon = if (isSpeaker) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, 
                                active = isSpeaker, 
                                accentColor = accentColor,
                                onClick = { CallRepo.toggleSpeaker(context, service) }
                            )
                            InCallToggle(
                                icon = Icons.Filled.Dialpad, 
                                active = showDtmf, 
                                accentColor = accentColor,
                                onClick = { showDtmf = true }
                            )
                        }
                        CallActionButton(
                            imageUri = settings.rejectButtonUri, 
                            icon = Icons.Filled.CallEnd, 
                            color = Color(0xFFF44336), 
                            label = "Auflegen", 
                            size = 112, 
                            onClick = { call.disconnect() }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun InCallToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    active: Boolean, 
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = CircleShape,
        color = if (active) accentColor else Color.White.copy(alpha = 0.15f),
        border = if (!active) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun CallActionButton(
    imageUri: String? = null, 
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    color: Color, 
    label: String, 
    size: Int = 112, 
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size.dp),
        shape = CircleShape,
        color = color,
        tonalElevation = 8.dp
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
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size((size * 0.45).dp))
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

@Composable
private fun DtmfPad(onTone: (Char) -> Unit, onStop: () -> Unit) {
    val scope = rememberCoroutineScope()
    val toneGenerator = remember {
        try { ToneGenerator(AudioManager.STREAM_DTMF, 80) } catch (_: Exception) { null }
    }
    
    DisposableEffect(Unit) {
        onDispose { toneGenerator?.release() }
    }

    fun playToneLocal(ch: Char) {
        val tone = when (ch) {
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '0' -> ToneGenerator.TONE_DTMF_0
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> -1
        }
        if (tone != -1) {
            toneGenerator?.startTone(tone, 150)
        }
    }

    Column(Modifier.padding(16.dp).navigationBarsPadding()) {
        val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf('*', '0', '#'))
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.forEach { ch ->
                    OutlinedButton(
                        onClick = { 
                            scope.launch { 
                                playToneLocal(ch)
                                onTone(ch)
                                delay(200)
                                onStop()
                            } 
                        }, 
                        modifier = Modifier.weight(1f).height(60.dp)
                    ) {
                        Text(ch.toString(), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private data class ContactInfo(val name: String?, val photoUri: String?)

private suspend fun lookupContactInfoByNumber(context: Context, num: String): ContactInfo = withContext(Dispatchers.IO) {
    if (num.isBlank()) return@withContext ContactInfo(null, null)
    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(num))
    context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI), null, null, null)?.use { c ->
        if (c.moveToFirst()) return@withContext ContactInfo(c.getString(0), c.getString(1))
    }
    ContactInfo(null, null)
}
