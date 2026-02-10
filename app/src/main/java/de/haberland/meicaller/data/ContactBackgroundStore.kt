package de.haberland.meicaller.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.contactBgStore by preferencesDataStore(name = "contact_backgrounds")

object ContactBackgroundStore {
    private fun keyFor(normalizedNumber: String) = stringPreferencesKey("bg_$normalizedNumber")

    fun backgroundUriFlow(
        context: Context,
        normalizedNumber: String,
    ): Flow<String?> =
        context.contactBgStore.data.map { prefs ->
            prefs[keyFor(normalizedNumber)]
        }

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

    suspend fun clearBackground(
        context: Context,
        normalizedNumber: String,
    ) {
        context.contactBgStore.edit { prefs ->
            prefs.remove(keyFor(normalizedNumber))
        }
    }
}
