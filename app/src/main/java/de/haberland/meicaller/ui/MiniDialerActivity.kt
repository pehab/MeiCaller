package de.haberland.meicaller.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import de.haberland.meicaller.data.UiSettings
import de.haberland.meicaller.data.UiSettingsStore
import de.haberland.meicaller.ui.theme.MeiCallerTheme

class MiniDialerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialNumber = intent?.data?.schemeSpecificPart.orEmpty()

        setContent {
            val settings by UiSettingsStore.flow(this)
                .collectAsState(initial = UiSettings())

            MeiCallerTheme(primaryHex = settings.primaryHex, accentHex = settings.accentHex) {
                Surface(Modifier) {
                    DialerTabScreen(
                        settings = settings,
                        initialNumber = initialNumber
                    )
                }
            }
        }
    }
}
