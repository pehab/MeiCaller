package de.haberland.meicaller.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Data class holding customizable UI settings for the application.
 * @property primaryHex The primary theme color in hex format.
 * @property accentHex The accent theme color in hex format.
 * @property backgroundUri URI for the global call background image.
 * @property acceptButtonUri URI for the custom accept call button image.
 * @property rejectButtonUri URI for the custom reject call button image.
 */
data class UiSettings(
    val primaryHex: String = "#B39DDB", // Default Light Purple
    val accentHex: String = "#7C4DFF", // Default Accent Purple
    val backgroundUri: String? = null,
    val acceptButtonUri: String? = null,
    val rejectButtonUri: String? = null,
)

private val Context.dataStore by preferencesDataStore(name = "ui_settings")

/**
 * Manages the storage and retrieval of UI settings using Jetpack DataStore.
 * It provides a flow to observe settings changes and functions to update them.
 */
object UiSettingsStore {
    private val KEY_PRIMARY = stringPreferencesKey("primaryHex")
    private val KEY_ACCENT = stringPreferencesKey("accentHex")
    private val KEY_BG = stringPreferencesKey("backgroundUri")
    private val KEY_ACCEPT = stringPreferencesKey("acceptButtonUri")
    private val KEY_REJECT = stringPreferencesKey("rejectButtonUri")

    /** Returns a flow that emits the latest UiSettings whenever they change. */
    fun flow(context: Context): Flow<UiSettings> =
        context.dataStore.data.map { prefs ->
            UiSettings(
                primaryHex = prefs[KEY_PRIMARY] ?: "#B39DDB",
                accentHex = prefs[KEY_ACCENT] ?: "#7C4DFF",
                backgroundUri = prefs[KEY_BG],
                acceptButtonUri = prefs[KEY_ACCEPT],
                rejectButtonUri = prefs[KEY_REJECT],
            )
        }

    /** Updates the primary theme color. */
    suspend fun setPrimary(
        context: Context,
        hex: String,
    ) {
        context.dataStore.edit { it[KEY_PRIMARY] = hex }
    }

    /** Updates the accent theme color. */
    suspend fun setAccent(
        context: Context,
        hex: String,
    ) {
        context.dataStore.edit { it[KEY_ACCENT] = hex }
    }

    /** Sets or removes the global background image URI. */
    suspend fun setBackgroundUri(
        context: Context,
        uri: Uri?,
    ) {
        context.dataStore.edit {
            if (uri == null) it.remove(KEY_BG) else it[KEY_BG] = uri.toString()
        }
    }

    /** Sets or removes the custom accept call button image URI. */
    suspend fun setAcceptButtonUri(
        context: Context,
        uri: Uri?,
    ) {
        context.dataStore.edit {
            if (uri == null) it.remove(KEY_ACCEPT) else it[KEY_ACCEPT] = uri.toString()
        }
    }

    /** Sets or removes the custom reject call button image URI. */
    suspend fun setRejectButtonUri(
        context: Context,
        uri: Uri?,
    ) {
        context.dataStore.edit {
            if (uri == null) it.remove(KEY_REJECT) else it[KEY_REJECT] = uri.toString()
        }
    }
}
