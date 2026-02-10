package de.haberland.meicaller

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private enum class TabItem(
    val label: String,
) {
    Dialer("Dialer"),
    Favorites("Favoriten"),
    CallLog("Anrufliste"),
    Contacts("Kontakte"),
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
                                contentDescription = "Einstellungen",
                            )
                        }
                    },
                )

                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    Tab(
                        selected = tab == TabItem.Dialer,
                        onClick = { tab = TabItem.Dialer },
                        text = { Text(TabItem.Dialer.label) },
                        icon = { Icon(Icons.Filled.Call, contentDescription = null) },
                    )
                    Tab(
                        selected = tab == TabItem.Favorites,
                        onClick = { tab = TabItem.Favorites },
                        text = { Text(TabItem.Favorites.label) },
                        icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    )
                    Tab(
                        selected = tab == TabItem.CallLog,
                        onClick = { tab = TabItem.CallLog },
                        text = { Text(TabItem.CallLog.label) },
                        icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    )
                    Tab(
                        selected = tab == TabItem.Contacts,
                        onClick = {
                            // System-Kontakte öffnen
                            val i =
                                Intent(Intent.ACTION_VIEW).apply {
                                    type = "vnd.android.cursor.dir/contact"
                                }
                            context.startActivity(i)
                        },
                        text = { Text(TabItem.Contacts.label) },
                        icon = { Icon(Icons.Filled.Contacts, contentDescription = null) },
                    )
                }
            }
        },
    ) { pad ->
        Box(
            modifier =
                Modifier
                    .padding(pad)
                    .fillMaxSize(),
        ) {
            when (tab) {
                TabItem.Dialer ->
                    DialerTabScreen(
                        settings = settings,
                    )
                TabItem.Favorites -> FavoritesTabScreen()
                TabItem.CallLog -> CallLogTabScreen()
                TabItem.Contacts -> {
                    // Wird per Intent geöffnet; optionaler Hinweis
                    Text("Kontakte-App wird geöffnet…", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
