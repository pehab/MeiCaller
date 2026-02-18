package de.haberland.meicaller.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import de.haberland.meicaller.data.ContactBackgroundStore
import de.haberland.meicaller.util.normalizeForCompare
import de.haberland.meicaller.util.placeCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FavoriteContact(
    val contactId: Long,
    val name: String,
    val number: String,
    val photoUri: String?,
)

@Composable
fun FavoritesTabScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    val favorites by produceState(initialValue = emptyList(), hasContacts) {
        value = if (hasContacts) loadFavorites(context) else emptyList()
    }

    var pendingBgKey by remember { mutableStateOf<String?>(null) }
    val pickBg = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val key = pendingBgKey
        if (uri != null && !key.isNullOrBlank()) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            scope.launch { ContactBackgroundStore.setBackground(context, key, uri) }
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text("Favoriten", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))

        if (!hasContacts) {
            Text("Zugriff auf Kontakte fehlt.", modifier = Modifier.padding(16.dp))
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(favorites) { f ->
                val normalized = remember(f.number) { normalizeForCompare(f.number) }
                val bgUri by ContactBackgroundStore.backgroundUriFlow(context, normalized).collectAsState(initial = null)

                FavoriteRow(
                    item = f,
                    hasCustomBackground = !bgUri.isNullOrBlank(),
                    onCall = { placeCall(context, f.number) },
                    onSetBackground = {
                        pendingBgKey = normalized
                        pickBg.launch(arrayOf("image/*"))
                    },
                    onClearBackground = {
                        scope.launch { ContactBackgroundStore.clearBackground(context, normalized) }
                    },
                )
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    item: FavoriteContact,
    hasCustomBackground: Boolean,
    onCall: () -> Unit,
    onSetBackground: () -> Unit,
    onClearBackground: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable { onCall() },
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!item.photoUri.isNullOrBlank()) {
                AsyncImage(model = item.photoUri, contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Surface(modifier = Modifier.size(48.dp), shape = CircleShape, tonalElevation = 2.dp) {
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

            IconButton(onClick = onSetBackground) { Icon(Icons.Filled.Image, contentDescription = null) }
            if (hasCustomBackground) {
                IconButton(onClick = onClearBackground) { Icon(Icons.Filled.Delete, contentDescription = null) }
            }
            IconButton(onClick = onCall) { Icon(Icons.Filled.Call, contentDescription = null) }
        }
    }
}

private suspend fun loadFavorites(context: Context): List<FavoriteContact> = withContext(Dispatchers.IO) {
    val out = mutableListOf<FavoriteContact>()
    val cr = context.contentResolver
    val proj = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.PHOTO_URI)
    val sel = "${ContactsContract.Contacts.STARRED}=1 AND ${ContactsContract.Contacts.HAS_PHONE_NUMBER}=1"
    
    cr.query(ContactsContract.Contacts.CONTENT_URI, proj, sel, null, "${ContactsContract.Contacts.DISPLAY_NAME} COLLATE NOCASE ASC")?.use { c ->
        while (c.moveToNext() && out.size < 200) {
            val id = c.getLong(0)
            val name = c.getString(1) ?: continue
            val photo = c.getString(2)
            val num = firstPhoneNumber(context, id) ?: continue
            out.add(FavoriteContact(id, name, num, photo))
        }
    }
    out
}

private fun firstPhoneNumber(context: Context, id: Long): String? {
    val sel = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?"
    context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), sel, arrayOf(id.toString()), null)?.use { c ->
        if (c.moveToFirst()) return c.getString(0)
    }
    return null
}
