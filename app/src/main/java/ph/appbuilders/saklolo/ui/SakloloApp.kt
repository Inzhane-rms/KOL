package ph.appbuilders.saklolo.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ph.appbuilders.saklolo.SakloloViewModel
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.relay.RelayService
import ph.appbuilders.saklolo.ui.theme.ForestDeep
import ph.appbuilders.saklolo.ui.theme.ForestMid
import ph.appbuilders.saklolo.ui.theme.ForestMint
import ph.appbuilders.saklolo.ui.theme.Ink

private const val RECORD = "record"
private const val FEED = "feed"

@Composable
fun SakloloApp(viewModel: SakloloViewModel) {
    val sos by viewModel.sos.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var route by remember { mutableStateOf(RECORD) }
    var settingsOpen by remember { mutableStateOf(false) }
    var qrAlert by remember { mutableStateOf<Alert?>(null) }
    var askedBattery by remember { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val locationOk = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationOk) viewModel.onLocationPermissionGranted()
        if (!askedBattery) {
            askedBattery = true
            (context as? Activity)?.let { askBatteryExemption(it) }
        }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::ingestQr)
    }

    LaunchedEffect(Unit) {
        permissions.launch(requiredPermissions())
        viewModel.onLocationPermissionGranted()
        ContextCompat.startForegroundService(context, Intent(context, RelayService::class.java))
    }

    val lastAlert = sos.sentAlertId?.let { id -> alerts.firstOrNull { it.id == id } }
        ?: alerts.firstOrNull { viewModel.clipReady(it) && it.hops == 0 }
        ?: alerts.firstOrNull { it.hops == 0 }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to ForestDeep,
                    0.55f to ForestMid,
                    1f to ForestMint,
                ),
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(
                peerCount = peers.size,
                onSettings = { settingsOpen = true },
            )
            Box(Modifier.weight(1f)) {
                if (route == RECORD) {
                    SosScreen(
                        state = sos,
                        peers = peers,
                        lastAlert = if (sos.actionable) null else lastAlert,
                        clipReady = viewModel::clipReady,
                        onHoldStart = {
                            if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                viewModel.startRecording()
                            } else {
                                permissions.launch(requiredPermissions())
                            }
                        },
                        onHoldEnd = { if (sos.recording) viewModel.stopRecording() },
                        onTranscript = viewModel::onTranscriptChange,
                        onSend = viewModel::sendDraft,
                        onDiscard = viewModel::discardDraft,
                        onPlay = viewModel::playClip,
                    )
                } else {
                    ResponderScreen(
                        alerts = alerts,
                        notice = notice,
                        clipReady = viewModel::clipReady,
                        onScan = {
                            if (hasPermission(context, Manifest.permission.CAMERA)) {
                                scanner.launch(scanOptions())
                            } else {
                                permissions.launch(requiredPermissions())
                            }
                        },
                        onDelete = viewModel::removeAlert,
                        onShowQr = { qrAlert = it },
                        onPlay = viewModel::playClip,
                        onDismissNotice = viewModel::clearNotice,
                    )
                }
            }
            BottomSwitcher(
                recordSelected = route == RECORD,
                onRecord = { route = RECORD },
                onFeed = { route = FEED },
            )
        }
    }

    if (settingsOpen) {
        SettingsDialog(
            initial = viewModel.demoConfig(),
            onDismiss = { settingsOpen = false },
            onSave = { name, restrict, allowlist, language ->
                viewModel.applyDemo(name, restrict, allowlist, language)
                settingsOpen = false
            },
        )
    }
    qrAlert?.let { alert ->
        QrDialog(alert = alert, payload = viewModel.qrText(alert), onDismiss = { qrAlert = null })
    }
}

@Composable
private fun Header(peerCount: Int, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("B-LINK", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(
            text = formatNearby(peerCount),
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        IconButton(onClick = onSettings, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
        }
    }
}

@Composable
private fun QrDialog(alert: Alert, payload: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(alert.summary, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Image(
                bitmap = qrBitmap(payload).asImageBitmap(),
                contentDescription = "Alert QR code",
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(240.dp),
            )
            TextButton(onClick = onDismiss) {
                Text("Close", color = Ink)
            }
        }
    }
}

private fun scanOptions(): ScanOptions = ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    setPrompt("Scan a B-LINK alert")
    setBeepEnabled(false)
    setOrientationLocked(true)
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
        permissions += Manifest.permission.POST_NOTIFICATIONS
    }
    return permissions.toTypedArray()
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun askBatteryExemption(activity: Activity) {
    val manager = activity.getSystemService(PowerManager::class.java) ?: return
    if (manager.isIgnoringBatteryOptimizations(activity.packageName)) return
    val intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${activity.packageName}"),
    )
    runCatching { activity.startActivity(intent) }
}
