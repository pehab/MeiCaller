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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
                    MainScreen(settings = settings)
                }
            }
        }
    }
}

private enum class TabItem(
    val labelRes: Int,
    val icon: ImageVector,
) {
    Dialer(R.string.tab_dialer, Icons.Filled.Call),
    Favorites(R.string.tab_favorites, Icons.Filled.Star),
    CallLog(R.string.tab_call_log, Icons.Filled.History),
    Contacts(R.string.tab_contacts, Icons.Filled.Contacts),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(settings: UiSettings) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(TabItem.Dialer) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        }) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    },
                )

                PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    TabItem.entries.forEach { item ->
                        Tab(
                            selected = selectedTab == item,
                            onClick = {
                                if (item == TabItem.Contacts) {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        type = "vnd.android.cursor.dir/contact"
                                    }
                                    context.startActivity(intent)
                                } else {
                                    selectedTab = item
                                }
                            },
                            text = { Text(stringResource(item.labelRes)) },
                            icon = { Icon(item.icon, contentDescription = null) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
        ) {
            when (selectedTab) {
                TabItem.Dialer -> DialerTabScreen(settings = settings)
                TabItem.Favorites -> FavoritesTabScreen()
                TabItem.CallLog -> CallLogTabScreen()
                TabItem.Contacts -> { /* Wird extern abgehandelt */ }
            }
        }
    }
}
