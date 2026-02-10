package de.haberland.meicaller.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import de.haberland.meicaller.data.UiSettings
import de.haberland.meicaller.data.UiSettingsStore
import de.haberland.meicaller.ui.theme.MeiCallerTheme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    private val pickBackground =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                persistAndSave(it) { u ->
                    lifecycleScope.launch { UiSettingsStore.setBackgroundUri(this@SettingsActivity, u) }
                }
            }
        }

    private val pickAccept =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                persistAndSave(it) { u ->
                    lifecycleScope.launch { UiSettingsStore.setAcceptButtonUri(this@SettingsActivity, u) }
                }
            }
        }

    private val pickReject =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                persistAndSave(it) { u ->
                    lifecycleScope.launch { UiSettingsStore.setRejectButtonUri(this@SettingsActivity, u) }
                }
            }
        }

    private fun persistAndSave(
        uri: Uri,
        save: (Uri) -> Unit,
    ) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Throwable) {
            // je nach Provider nicht möglich – dann evtl. nicht reboot-sicher
        }
        save(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settings by UiSettingsStore.flow(this).collectAsState(initial = UiSettings())

            MeiCallerTheme(
                primaryHex = settings.primaryHex,
                accentHex = settings.accentHex,
            ) {
                Surface(Modifier.fillMaxSize()) {
                    SettingsScreen(
                        settings = settings,
                        onSavePrimary = { hex ->
                            lifecycleScope.launch { UiSettingsStore.setPrimary(this@SettingsActivity, hex) }
                        },
                        onSaveAccent = { hex ->
                            lifecycleScope.launch { UiSettingsStore.setAccent(this@SettingsActivity, hex) }
                        },
                        onPickBackground = { pickBackground.launch(arrayOf("image/*")) },
                        onClearBackground = {
                            lifecycleScope.launch { UiSettingsStore.setBackgroundUri(this@SettingsActivity, null) }
                        },
                        onPickAccept = { pickAccept.launch(arrayOf("image/*")) },
                        onClearAccept = {
                            lifecycleScope.launch { UiSettingsStore.setAcceptButtonUri(this@SettingsActivity, null) }
                        },
                        onPickReject = { pickReject.launch(arrayOf("image/*")) },
                        onClearReject = {
                            lifecycleScope.launch { UiSettingsStore.setRejectButtonUri(this@SettingsActivity, null) }
                        },
                        onClose = { finish() },
                    )
                }
            }
        }
    }
}

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
    // lokale Edit-Felder (damit Live-Update nicht beim Tippen “springt”)
    var primary by remember(settings.primaryHex) { mutableStateOf(settings.primaryHex) }
    var accent by remember(settings.accentHex) { mutableStateOf(settings.accentHex) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text("MeiCaller – Design", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        // Farben
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Farben", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = primary,
                    onValueChange = { primary = it },
                    label = { Text("Primary (Hex, z.B. #00E5FF)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onSavePrimary(primary.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Primary speichern")
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = accent,
                    onValueChange = { accent = it },
                    label = { Text("Accent (Hex, z.B. #7C4DFF)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onSaveAccent(accent.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Accent speichern")
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Tipp: Hex kann #RRGGBB oder #AARRGGBB sein.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Hintergrund
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Hintergrund", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))

                if (!settings.backgroundUri.isNullOrBlank()) {
                    AsyncImage(
                        model = settings.backgroundUri,
                        contentDescription = "Background preview",
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(10.dp))
                } else {
                    Text("Kein Hintergrundbild gesetzt.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onPickBackground,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Wählen") }

                    OutlinedButton(
                        onClick = onClearBackground,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Reset") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Accept/Reject Icons
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Incoming-Buttons", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    IconPicker(
                        title = "Annehmen",
                        uri = settings.acceptButtonUri,
                        onPick = onPickAccept,
                        onClear = onClearAccept,
                        modifier = Modifier.weight(1f),
                    )
                    IconPicker(
                        title = "Ablehnen",
                        uri = settings.rejectButtonUri,
                        onPick = onPickReject,
                        onClear = onClearReject,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Schließen") }
    }
}

@Composable
private fun IconPicker(
    title: String,
    uri: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))

        if (!uri.isNullOrBlank()) {
            AsyncImage(
                model = uri,
                contentDescription = "$title icon preview",
                modifier =
                    Modifier
                        .size(92.dp)
                        .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Surface(
                modifier = Modifier.size(92.dp),
                shape = CircleShape,
                tonalElevation = 2.dp,
            ) {}
        }

        Spacer(Modifier.height(10.dp))

        Button(onClick = onPick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("Wählen")
        }

        OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("Reset")
        }
    }
}
