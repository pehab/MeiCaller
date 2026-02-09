package de.haberland.meicaller

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.haberland.meicaller.data.UiSettings
import de.haberland.meicaller.data.UiSettingsStore
import de.haberland.meicaller.ui.CallLogTabScreen
import de.haberland.meicaller.ui.DialerTabScreen
import de.haberland.meicaller.ui.FavoritesTabScreen
import de.haberland.meicaller.ui.SettingsActivity
import de.haberland.meicaller.ui.theme.MeiCallerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settings by UiSettingsStore.flow(this).collectAsState(initial = UiSettings())

            MeiCallerTheme(primaryHex = settings.primaryHex, accentHex = settings.accentHex) {
                Surface(Modifier.fillMaxSize()) {
                    MainTabs(settings = settings)
                }
            }
        }
    }
}

private enum class TabItem(val label: String) {
    Dialer("Dialer"),
    Favorites("Favoriten"),
    CallLog("Anrufliste"),
    Contacts("Kontakte")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabs(settings: UiSettings) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(TabItem.Dialer) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("MeiCaller") },
                    actions = {
                        // Optional: Settings immer erreichbar
                        IconButton(onClick = {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        }) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Einstellungen"
                            )
                        }
                    }
                )

                TabRow(selectedTabIndex = tab.ordinal) {
                    Tab(
                        selected = tab == TabItem.Dialer,
                        onClick = { tab = TabItem.Dialer },
                        text = { Text(TabItem.Dialer.label) },
                        icon = { Icon(Icons.Filled.Call, contentDescription = null) }
                    )
                    Tab(
                        selected = tab == TabItem.Favorites,
                        onClick = { tab = TabItem.Favorites },
                        text = { Text(TabItem.Favorites.label) },
                        icon = { Icon(Icons.Filled.Star, contentDescription = null) }
                    )
                    Tab(
                        selected = tab == TabItem.CallLog,
                        onClick = { tab = TabItem.CallLog },
                        text = { Text(TabItem.CallLog.label) },
                        icon = { Icon(Icons.Filled.History, contentDescription = null) }
                    )
                    Tab(
                        selected = tab == TabItem.Contacts,
                        onClick = {
                            // System-Kontakte öffnen
                            val i = Intent(Intent.ACTION_VIEW).apply {
                                type = "vnd.android.cursor.dir/contact"
                            }
                            context.startActivity(i)
                        },
                        text = { Text(TabItem.Contacts.label) },
                        icon = { Icon(Icons.Filled.Contacts, contentDescription = null) }
                    )
                }
            }
        }
    ) { pad ->
        Box(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            when (tab) {
                TabItem.Dialer -> DialerTabScreen(
                    settings = settings
                )
                TabItem.Favorites -> FavoritesTabScreen(settings = settings)
                TabItem.CallLog -> CallLogTabScreen(settings = settings)
                TabItem.Contacts -> {
                    // Wird per Intent geöffnet; optionaler Hinweis
                    Text("Kontakte-App wird geöffnet…", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
