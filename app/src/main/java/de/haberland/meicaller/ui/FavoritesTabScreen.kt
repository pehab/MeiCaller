package de.haberland.meicaller.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import coil.compose.AsyncImage
import de.haberland.meicaller.data.UiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FavoriteContact(
    val contactId: Long,
    val name: String,
    val number: String,
    val photoUri: String?
)

@Composable
fun FavoritesTabScreen(settings: UiSettings) {
    val context = LocalContext.current

    val hasContacts =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED

    val favorites by produceState(initialValue = emptyList<FavoriteContact>(), hasContacts) {
        value = if (hasContacts) loadFavorites(context) else emptyList()
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text("Favoriten", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))

        if (!hasContacts) {
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("MeiCaller braucht Zugriff auf Kontakte, um Favoriten anzuzeigen.")
                    Spacer(Modifier.height(8.dp))
                    Text("→ Bitte in den App-Berechtigungen „Kontakte“ erlauben.")
                }
            }
            return
        }

        if (favorites.isEmpty()) {
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Keine Favoriten gefunden.")
                    Text("Tipp: Markiere Kontakte in der Kontakte-App als Favorit ⭐", style = MaterialTheme.typography.bodySmall)
                }
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(favorites) { f ->
                FavoriteRow(
                    item = f,
                    onCall = { placeCall(context, f.number) }
                )
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    item: FavoriteContact,
    onCall: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCall() }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kontaktbild (falls vorhanden)
            if (!item.photoUri.isNullOrBlank()) {
                AsyncImage(
                    model = item.photoUri,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    tonalElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(item.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.number, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            IconButton(onClick = onCall) {
                Icon(Icons.Filled.Call, contentDescription = "Anrufen")
            }
        }
    }
}

private suspend fun loadFavorites(context: Context): List<FavoriteContact> = withContext(Dispatchers.IO) {
    val out = mutableListOf<FavoriteContact>()
    val cr = context.contentResolver

    val projection = arrayOf(
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.DISPLAY_NAME,
        ContactsContract.Contacts.PHOTO_URI
    )

    val sel = "${ContactsContract.Contacts.STARRED}=1 AND ${ContactsContract.Contacts.HAS_PHONE_NUMBER}=1"
    cr.query(
        ContactsContract.Contacts.CONTENT_URI,
        projection,
        sel,
        null,
        "${ContactsContract.Contacts.DISPLAY_NAME} COLLATE NOCASE ASC"
    )?.use { c ->
        while (c.moveToNext() && out.size < 200) {
            val id = c.getLong(0)
            val name = c.getString(1) ?: continue
            val photoUri = c.getString(2)

            val number = firstPhoneNumberForContact(context, id) ?: continue
            out.add(
                FavoriteContact(
                    contactId = id,
                    name = name,
                    number = number,
                    photoUri = photoUri
                )
            )
        }
    }

    out
}

private fun firstPhoneNumberForContact(context: Context, contactId: Long): String? {
    val cr = context.contentResolver
    val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
    val sel = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?"
    val args = arrayOf(contactId.toString())

    cr.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        sel,
        args,
        null
    )?.use { c ->
        return if (c.moveToFirst()) c.getString(0) else null
    }
    return null
}

private fun placeCall(context: Context, number: String) {
    val clean = number.trim()
    if (clean.isEmpty()) return

    val uri = "tel:$clean".toUri()
    val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    try {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            // Kein CALL_PHONE: fallback auf DIAL
            context.startActivity(Intent(Intent.ACTION_DIAL, uri))
            return
        }
        telecom.placeCall(uri, null)
    } catch (_: Throwable) {
        context.startActivity(Intent(Intent.ACTION_DIAL, uri))
    }
}
