package de.haberland.meicaller

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
                    var isConfigured by remember { mutableStateOf(false) }
                    val context = LocalContext.current

                    fun checkConfig() {
                        val hasPerms = REQUIRED_PERMISSIONS.all {
                            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                        }
                        val isDialer = isDefaultDialer(context)
                        isConfigured = hasPerms && isDialer
                    }

                    LaunchedEffect(Unit) {
                        checkConfig()
                    }

                    if (isConfigured) {
                        MainScreen(settings = settings)
                    } else {
                        SetupScreen(onConfigChanged = { checkConfig() })
                    }
                }
            }
        }
    }

    private fun isDefaultDialer(context: Context): Boolean {
        val telecomManager = context.getSystemService(TELECOM_SERVICE) as TelecomManager
        return telecomManager.defaultDialerPackage == context.packageName
    }

    companion object {
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG
        )
    }
}

@Composable
private fun SetupScreen(onConfigChanged: () -> Unit) {
    val context = LocalContext.current
    val REQUIRED_PERMISSIONS = MainActivity.REQUIRED_PERMISSIONS

    var hasPermissions by remember {
        mutableStateOf(REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    var isDefaultDialer by remember {
        mutableStateOf(telecomManager.defaultDialerPackage == context.packageName)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        onConfigChanged()
    }

    val dialerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultDialer = telecomManager.defaultDialerPackage == context.packageName
        onConfigChanged()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = "Konfiguration erforderlich",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            text = "Damit MeiCaller Anrufe verwalten kann, muss die App als Standard-Telefon-App festgelegt sein und Zugriff auf deine Kontakte und Anrufe haben.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(40.dp))

        SetupStep(
            title = "Berechtigungen",
            description = "Kontakte, Anrufe und Telefonstatus",
            isDone = hasPermissions,
            onClick = { permissionLauncher.launch(REQUIRED_PERMISSIONS) }
        )

        Spacer(Modifier.height(16.dp))

        SetupStep(
            title = "Standard-Telefon-App",
            description = "MeiCaller für Anrufe verwenden",
            isDone = isDefaultDialer,
            onClick = {
                val roleManager = context.getSystemService(RoleManager::class.java)
                val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                if (intent != null) dialerLauncher.launch(intent)
            }
        )
    }
}

@Composable
private fun SetupStep(
    title: String,
    description: String,
    isDone: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = if (!isDone) onClick else ({}),
        shape = MaterialTheme.shapes.medium,
        color = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
                else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            Icon(
                imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.Settings,
                contentDescription = null,
                tint = if (isDone) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onPrimaryContainer
            )
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
                TabItem.Contacts -> { /* Externally handled */ }
            }
        }
    }
}
