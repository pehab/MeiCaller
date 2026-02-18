package de.haberland.meicaller.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri

/**
 * Data class representing a contact's display information.
 */
data class ContactInfo(
    val name: String?,
    val photoUri: Uri? = null
)

/**
 * Places a call using the TelecomManager if permissions allow,
 * otherwise falls back to showing the system dialer.
 */
fun placeCall(
    context: Context,
    number: String,
) {
    val cleanNumber = number.trim()
    if (cleanNumber.isEmpty()) return

    val uri = Uri.fromParts("tel", cleanNumber, null)
    val dialIntent = Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
        val telecomManager = context.getSystemService<TelecomManager>()
        try {
            telecomManager?.placeCall(uri, null)
            return
        } catch (_: Exception) {
            // Fallback to dial intent below
        }
    }
    context.startActivity(dialIntent)
}

/**
 * Resolves a phone number to a contact name.
 */
fun getContactName(context: Context, number: String?): String? {
    return getContactInfo(context, number).name
}

/**
 * Resolves a phone number to a contact name and photo URI.
 * Requires READ_CONTACTS permission.
 */
fun getContactInfo(context: Context, number: String?): ContactInfo {
    if (number.isNullOrBlank()) return ContactInfo(null)
    
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return ContactInfo(null)
    }

    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    val projection = arrayOf(
        ContactsContract.PhoneLookup.DISPLAY_NAME,
        ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
    )

    try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                val photoUriString = cursor.getString(1)
                return ContactInfo(
                    name = name,
                    photoUri = photoUriString?.toUri()
                )
            }
        }
    } catch (_: Exception) {}
    return ContactInfo(null)
}

/**
 * Opens the system "Add Contact" screen for a given number.
 */
fun addToContacts(
    context: Context,
    number: String,
) {
    val intent =
        Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number.trim())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    context.startActivity(intent)
}

/**
 * Simple helper to normalize numbers for comparison.
 */
fun normalizeForCompare(number: String): String {
    return number.filter { it.isDigit() }
}
