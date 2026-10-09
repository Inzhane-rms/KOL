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
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ph.appbuilders.saklolo.SakloloViewModel
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.relay.RelayPermissions
import ph.appbuilders.saklolo.relay.RelayService
import ph.appbuilders.saklolo.triage.Urgency
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.Page

private const val RECORD = "record"
private const val ASK = "ask"
private const val FEED = "feed"

@Composable
fun SakloloApp(viewModel: SakloloViewModel) {
    val sos by viewModel.sos.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val askTurns by viewModel.askTurns.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var route by remember { mutableStateOf(RECORD) }
    var settingsOpen by remember { mutableStateOf(false) }
    var qrAlert by remember { mutableStateOf<Alert?>(null) }
    var askedBattery by remember { mutableStateOf(false) }
    var askedMic by remember { mutableStateOf(false) }
    var micBlocked by remember { mutableStateOf(false) }
    var micGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO)) }
    var preciseBlocked by remember { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        askedMic = true
        val locationOk = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationOk) viewModel.onLocationPermissionGranted()
        syncPermissions(
            context,
            askedMic = true,
            onMicBlocked = { micBlocked = it },
            onMicGranted = { micGranted = it },
            onPreciseBlocked = { preciseBlocked = it },
        )
        if (!askedBattery) {
            askedBattery = true
            (context as? Activity)?.let { askBatteryExemption(it) }
        }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::ingestQr)
    }

    DisposableEffect(lifecycleOwner, context, askedMic) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val locationOk = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                    hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (locationOk) viewModel.onLocationPermissionGranted()
                syncPermissions(
                    context,
                    askedMic,
                    onMicBlocked = { micBlocked = it },
                    onMicGranted = { micGranted = it },
                    onPreciseBlocked = { preciseBlocked = it },
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (RelayPermissions.needsPreciseChoice(context)) {
            syncPermissions(
                context,
                askedMic,
                onMicBlocked = { micBlocked = it },
                onMicGranted = { micGranted = it },
                onPreciseBlocked = { preciseBlocked = it },
            )
        } else if (!RelayPermissions.granted(context)) {
            permissions.launch(requiredPermissions())
        } else {
            syncPermissions(
                context,
                askedMic,
                onMicBlocked = { micBlocked = it },
                onMicGranted = { micGranted = it },
                onPreciseBlocked = { preciseBlocked = it },
            )
        }
    }

    val location by viewModel.location.collectAsStateWithLifecycle()
    var askSeed by remember { mutableStateOf<String?>(null) }
    val localAlerts = alerts.filter { viewModel.isLocalOrigin(it.id) }
    val lastAlert = sos.sentAlertId?.let { id -> localAlerts.firstOrNull { it.id == id } }
        ?: localAlerts.maxByOrNull { it.createdAtMillis }
    val unrespondedCritical = alerts.count { it.urgency == Urgency.CRITICAL && !it.responding }

    Box(Modifier.fillMaxSize().background(Page)) {
        if (route == ASK) {
            AskScreen(
                turns = askTurns,
                onAsk = viewModel::submitAsk,
                onOpenRecorder = { route = RECORD },
                seed = askSeed,
                onSeedConsumed = { askSeed = null },
            )
        } else if (route == RECORD) {
            SosScreen(
                state = sos,
                peers = peers,
                location = location,
                lastAlert = if (sos.actionable) null else lastAlert,
                lastAlertLocal = lastAlert?.let { viewModel.isLocalOrigin(it.id) } == true,
                clipReady = viewModel::clipReady,
                onHoldStart = {
                    if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                        micGranted = true
                        micBlocked = false
                        viewModel.startRecording()
                    } else if (micIsPermanentlyDenied(context)) {
                        askedMic = true
                        micBlocked = true
                    } else {
                        permissions.launch(requiredPermissions())
                    }
                },
                onHoldEnd = { if (sos.recording) viewModel.stopRecording() },
                onTranscript = viewModel::onTranscriptChange,
                onSend = viewModel::sendDraft,
                onDiscard = viewModel::discardDraft,
                onPlay = viewModel::playClip,
                onOpenSettings = { settingsOpen = true },
                onSeeAll = { route = ASK },
                onTopic = { question ->
                    askSeed = question
                    route = ASK
                },
                onOpenAlerts = { route = FEED },
                micBlocked = micBlocked,
                micGranted = micGranted,
                preciseBlocked = preciseBlocked,
                onOpenAppSettings = { openAppSettings(context) },
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
                onMarkResponding = viewModel::markResponding,
                onOpenRecorder = { route = RECORD },
            )
        }
        BottomSwitcher(
            route = route,
            alertBadge = unrespondedCritical,
            onRecord = { route = RECORD },
            onAsk = { route = ASK },
            onFeed = { route = FEED },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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
private fun QrDialog(alert: Alert, payload: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(alert.summary, color = Ink, fontWeight = FontWeight.Bold, fontSize = 21.sp)
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

private fun syncPermissions(
    context: Context,
    askedMic: Boolean,
    onMicBlocked: (Boolean) -> Unit,
    onMicGranted: (Boolean) -> Unit,
    onPreciseBlocked: (Boolean) -> Unit,
) {
    onPreciseBlocked(RelayPermissions.needsPreciseChoice(context))
    onMicGranted(hasPermission(context, Manifest.permission.RECORD_AUDIO))
    if (RelayPermissions.granted(context)) {
        ContextCompat.startForegroundService(context, Intent(context, RelayService::class.java))
    }
    if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
        onMicBlocked(false)
    } else if (askedMic && micIsPermanentlyDenied(context)) {
        onMicBlocked(true)
    }
}

private fun micIsPermanentlyDenied(context: Context): Boolean {
    val activity = context as? Activity ?: return false
    if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) return false
    return !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )
    runCatching { context.startActivity(intent) }
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
