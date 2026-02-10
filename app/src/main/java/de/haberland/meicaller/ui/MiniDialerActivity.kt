package de.haberland.meicaller.ui

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

/**
 * A lightweight activity that opens a dialer pre-filled with a specific number.
 * This is typically used when the user clicks on a "Call Back" action from a missed call notification or list.
 */
class MiniDialerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract the phone number from the intent data (e.g., tel:123456)
        val initialNumber = intent?.data?.schemeSpecificPart.orEmpty()

        setContent {
            val settings by UiSettingsStore
                .flow(this)
                .collectAsState(initial = UiSettings())

            MeiCallerTheme(primaryHex = settings.primaryHex, accentHex = settings.accentHex) {
                Surface(Modifier) {
                    // Reuse the DialerTabScreen component to provide a consistent dialing experience
                    DialerTabScreen(
                        settings = settings,
                        initialNumber = initialNumber,
                    )
                }
            }
        }
    }
}
