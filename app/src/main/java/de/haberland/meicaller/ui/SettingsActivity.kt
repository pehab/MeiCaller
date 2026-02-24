package de.haberland.meicaller.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import de.haberland.meicaller.BuildConfig
import de.haberland.meicaller.R
import de.haberland.meicaller.data.UiSettings
import de.haberland.meicaller.data.UiSettingsStore
import de.haberland.meicaller.ui.theme.MeiCallerTheme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    private val pickBackground = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persistAndSave(it) { u -> lifecycleScope.launch { UiSettingsStore.setBackgroundUri(this@SettingsActivity, u) } } }
    }

    private val pickAccept = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persistAndSave(it) { u -> lifecycleScope.launch { UiSettingsStore.setAcceptButtonUri(this@SettingsActivity, u) } } }
    }

    private val pickReject = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persistAndSave(it) { u -> lifecycleScope.launch { UiSettingsStore.setRejectButtonUri(this@SettingsActivity, u) } } }
    }

    private fun persistAndSave(uri: Uri, save: (Uri) -> Unit) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {}
        save(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by UiSettingsStore.flow(this).collectAsState(initial = UiSettings())
            MeiCallerTheme(primaryHex = settings.primaryHex, accentHex = settings.accentHex) {
                SettingsScreen(
                    settings = settings,
                    onSavePrimary = { lifecycleScope.launch { UiSettingsStore.setPrimary(this@SettingsActivity, it) } },
                    onSaveAccent = { lifecycleScope.launch { UiSettingsStore.setAccent(this@SettingsActivity, it) } },
                    onPickBackground = { pickBackground.launch(arrayOf("image/*")) },
                    onClearBackground = { lifecycleScope.launch { UiSettingsStore.setBackgroundUri(this@SettingsActivity, null) } },
                    onPickAccept = { pickAccept.launch(arrayOf("image/*")) },
                    onClearAccept = { lifecycleScope.launch { UiSettingsStore.setAcceptButtonUri(this@SettingsActivity, null) } },
                    onPickReject = { pickReject.launch(arrayOf("image/*")) },
                    onClearReject = { lifecycleScope.launch { UiSettingsStore.setRejectButtonUri(this@SettingsActivity, null) } },
                    onClose = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    settings: UiSettings,
    onSavePrimary: (String) -> Unit,
    onSaveAccent: (String) -> Unit,
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit,
    onPickAccept: () -> Unit,
    onClearAccept: () -> Unit,
    onPickReject: () -> Unit,
    onClearReject: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Colors Section
            SettingsSection(title = stringResource(R.string.settings_section_colors)) {
                ColorPickerInput(
                    label = stringResource(R.string.settings_primary_color),
                    initialHex = settings.primaryHex,
                    onSave = onSavePrimary
                )
                Spacer(Modifier.height(24.dp))
                ColorPickerInput(
                    label = stringResource(R.string.settings_accent_color),
                    initialHex = settings.accentHex,
                    onSave = onSaveAccent
                )
            }

            // Background Section
            SettingsSection(title = stringResource(R.string.settings_section_background)) {
                if (!settings.backgroundUri.isNullOrBlank()) {
                    AsyncImage(
                        model = settings.backgroundUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPickBackground, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.btn_pick))
                    }
                    OutlinedButton(onClick = onClearBackground, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.btn_reset))
                    }
                }
            }

            // Call Buttons Section
            SettingsSection(title = stringResource(R.string.settings_section_buttons)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ImagePickerItem(
                        title = stringResource(R.string.settings_btn_accept),
                        uri = settings.acceptButtonUri,
                        onPick = onPickAccept,
                        onClear = onClearAccept,
                        modifier = Modifier.weight(1f)
                    )
                    ImagePickerItem(
                        title = stringResource(R.string.settings_btn_reject),
                        uri = settings.rejectButtonUri,
                        onPick = onPickReject,
                        onClear = onClearReject,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Version Info
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ColorPickerInput(
    label: String,
    initialHex: String,
    onSave: (String) -> Unit
) {
    var text by remember(initialHex) { mutableStateOf(initialHex) }
    var showPicker by remember { mutableStateOf(false) }
    val parsedColor = remember(text) { parseHexSafe(text) }

    val presets = listOf("#2196F3", "#4CAF50", "#F44336", "#FF9800", "#9C27B0", "#795548", "#607D8B", "#000000")

    Column {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(parsedColor ?: Color.Gray)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { showPicker = !showPicker }
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyLarge
            )
        }

        if (showPicker) {
            Spacer(Modifier.height(16.dp))
            HueSlider(
                initialColor = parsedColor ?: Color.Blue,
                onColorChanged = { 
                    val hex = String.format("#%06X", (0xFFFFFF and it.toArgb()))
                    text = hex
                }
            )
            
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(p.toColorInt()))
                            .border(if (text.equals(p, ignoreCase = true)) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { text = p }
                    )
                }
            }
        }

        if (text.trim().uppercase() != initialHex.trim().uppercase() && parsedColor != null) {
            Button(
                onClick = { 
                    onSave(text.trim()) 
                    showPicker = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.btn_save))
            }
        }
    }
}

@Composable
private fun HueSlider(initialColor: Color, onColorChanged: (Color) -> Unit) {
    val hsv = remember(initialColor) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), out)
        out
    }
    var hue by remember(initialColor) { mutableFloatStateOf(hsv[0]) }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(
                            Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                        )
                    )
                )
        )
        Slider(
            value = hue,
            onValueChange = {
                hue = it
                onColorChanged(Color(android.graphics.Color.HSVToColor(floatArrayOf(it, 0.7f, 0.9f))))
            },
            valueRange = 0f..360f
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun ImagePickerItem(
    title: String,
    uri: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!uri.isNullOrBlank()) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text(stringResource(R.string.btn_pick))
        }
        OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text(stringResource(R.string.btn_reset))
        }
    }
}

private fun parseHexSafe(hex: String): Color? {
    return try {
        val clean = hex.trim().removePrefix("#")
        val v = clean.toLong(16)
        when (clean.length) {
            6 -> Color((0xFF000000 or v).toInt())
            8 -> Color(v.toInt())
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
