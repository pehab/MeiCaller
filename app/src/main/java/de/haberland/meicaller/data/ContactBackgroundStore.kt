package de.haberland.meicaller.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.contactBgStore by preferencesDataStore(name = "contact_backgrounds")

/**
 * Manages custom background images assigned to specific phone numbers.
 * It uses Jetpack DataStore to persist the mapping between normalized phone numbers and image URIs.
 */
object ContactBackgroundStore {
    /** Generates a unique preferences key for a given normalized phone number. */
    private fun keyFor(normalizedNumber: String) = stringPreferencesKey("bg_$normalizedNumber")

    /**
     * Returns a flow emitting the background image URI for a specific phone number.
     * @param normalizedNumber The phone number in normalized format.
     */
    fun backgroundUriFlow(
        context: Context,
        normalizedNumber: String,
    ): Flow<String?> =
        context.contactBgStore.data.map { prefs ->
            prefs[keyFor(normalizedNumber)]
        }

    /**
     * Sets or updates the background image URI for a specific phone number.
     * @param normalizedNumber The phone number in normalized format.
     * @param uri The URI of the selected image.
     */
    suspend fun setBackground(
        context: Context,
        normalizedNumber: String,
        uri: Uri?,
    ) {
        context.contactBgStore.edit { prefs ->
            val k = keyFor(normalizedNumber)
            if (uri == null) prefs.remove(k) else prefs[k] = uri.toString()
        }
    }

    /**
     * Removes the custom background assignment for a specific phone number.
     * @param normalizedNumber The phone number in normalized format.
     */
    suspend fun clearBackground(
        context: Context,
        normalizedNumber: String,
    ) {
        context.contactBgStore.edit { prefs ->
            prefs.remove(keyFor(normalizedNumber))
        }
    }
}
