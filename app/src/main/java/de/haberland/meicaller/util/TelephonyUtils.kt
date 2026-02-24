package de.haberland.meicaller.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
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
 * Places a call using the TelecomManager if the app is the default dialer,
 * otherwise falls back to showing the system dialer.
 */
fun placeCall(
    context: Context,
    number: String,
) {
    val cleanNumber = number.trim()
    if (cleanNumber.isEmpty()) return

    val uri = Uri.fromParts("tel", cleanNumber, null)
    val telecomManager = context.getSystemService<TelecomManager>()
    val isDefaultDialer = telecomManager?.defaultDialerPackage == context.packageName

    if (isDefaultDialer && ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
        try {
            telecomManager?.placeCall(uri, null)
            return
        } catch (e: Exception) {
            // Fallback if placeCall fails for some reason
        }
    }

    // Fallback: Open system dialer
    val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(dialIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Anruf konnte nicht gestartet werden", Toast.LENGTH_SHORT).show()
    }
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
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Kontakte-App nicht gefunden", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Simple helper to normalize numbers for comparison.
 */
fun normalizeForCompare(number: String): String {
    return number.filter { it.isDigit() }
}

/**
 * Marks all "new" missed calls as "seen/read" in the system call log.
 * Required WRITE_CALL_LOG permission.
 */
fun markMissedCallsAsSeen(context: Context) {
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
        return
    }
    
    val cr = context.contentResolver
    try {
        // Mark as 'not new'
        val valuesNew = ContentValues().apply { put(CallLog.Calls.NEW, 0) }
        cr.update(
            CallLog.Calls.CONTENT_URI,
            valuesNew,
            "${CallLog.Calls.TYPE}=? AND ${CallLog.Calls.NEW}=?",
            arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "1"),
        )

        // Mark as 'read'
        val valuesRead = ContentValues().apply { put("is_read", 1) }
        cr.update(
            CallLog.Calls.CONTENT_URI,
            valuesRead,
            "${CallLog.Calls.TYPE}=? AND (is_read=0 OR is_read IS NULL)",
            arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
        )

        // Notify providers to update system UI
        cr.notifyChange(CallLog.Calls.CONTENT_URI, null)
    } catch (t: Throwable) {
        Log.w("MeiCaller", "markMissedCallsAsSeen failed: ${t.message}")
    }
}
