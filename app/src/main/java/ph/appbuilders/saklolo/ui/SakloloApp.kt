package ph.appbuilders.saklolo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ph.appbuilders.saklolo.SakloloViewModel

private const val SOS = "sos"
private const val FEED = "feed"

@Composable
fun SakloloApp(viewModel: SakloloViewModel) {
    val sos by viewModel.sos.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val relay by viewModel.relayState.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val context = LocalContext.current
    var startRecordingAfterPermission by remember { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val locationOk = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationOk) viewModel.onLocationPermissionGranted()
        if (startRecordingAfterPermission && granted[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.startRecording()
        }
        startRecordingAfterPermission = false
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::ingestQr)
    }

    LaunchedEffect(Unit) {
        permissions.launch(requiredPermissions())
        viewModel.onLocationPermissionGranted()
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val route = nav.currentBackStackEntryAsState().value?.destination?.route
                NavigationBarItem(
                    selected = route == SOS,
                    onClick = { nav.navigate(SOS) { launchSingleTop = true } },
                    icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                    label = { Text("SOS") },
                )
                NavigationBarItem(
                    selected = route == FEED,
                    onClick = { nav.navigate(FEED) { launchSingleTop = true } },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text("Responders") },
                )
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = SOS) {
            composable(SOS) {
                SosScreen(
                    state = sos,
                    contentPadding = padding,
                    onLanguage = viewModel::setLanguage,
                    onRecordToggle = {
                        if (sos.recording) {
                            viewModel.stopRecording()
                        } else if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                            viewModel.startRecording()
                        } else {
                            startRecordingAfterPermission = true
                            permissions.launch(requiredPermissions())
                        }
                    },
                    onTranscript = viewModel::onTranscriptChange,
                    onSend = viewModel::sendDraft,
                    onDiscard = viewModel::discardDraft,
                )
            }
            composable(FEED) {
                ResponderScreen(
                    alerts = alerts,
                    relay = relay,
                    contentPadding = padding,
                    onRelay = { enabled ->
                        if (enabled && !hasNearbyPermissions(context)) {
                            permissions.launch(requiredPermissions())
                        }
                        viewModel.setRelayEnabled(enabled)
                    },
                    onScan = {
                        if (hasPermission(context, Manifest.permission.CAMERA)) {
                            scanner.launch(scanOptions())
                        } else {
                            permissions.launch(requiredPermissions())
                        }
                    },
                    onDelete = viewModel::removeAlert,
                    qrFor = viewModel::qrText,
                    onDismissNotice = viewModel::clearNotice,
                )
            }
        }
    }
}

private fun scanOptions(): ScanOptions = ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    setPrompt("Scan a Saklolo alert")
    setBeepEnabled(false)
    setOrientationLocked(false)
}

private fun requiredPermissions(): Array<String> {
    val permissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
    )
    if (Build.VERSION.SDK_INT >= 31) {
        permissions += Manifest.permission.BLUETOOTH_SCAN
        permissions += Manifest.permission.BLUETOOTH_ADVERTISE
        permissions += Manifest.permission.BLUETOOTH_CONNECT
    }
    if (Build.VERSION.SDK_INT >= 33) {
        permissions += Manifest.permission.NEARBY_WIFI_DEVICES
    }
    return permissions.toTypedArray()
}

private fun hasNearbyPermissions(context: android.content.Context): Boolean {
    val needed = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= 31) {
        needed += Manifest.permission.BLUETOOTH_SCAN
        needed += Manifest.permission.BLUETOOTH_ADVERTISE
        needed += Manifest.permission.BLUETOOTH_CONNECT
    }
    if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.NEARBY_WIFI_DEVICES
    return needed.all { hasPermission(context, it) }
}

private fun hasPermission(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
