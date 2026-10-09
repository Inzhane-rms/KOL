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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
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
import ph.appbuilders.saklolo.SosUiState
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.relay.RelayPermissions
import ph.appbuilders.saklolo.relay.RelayService
import ph.appbuilders.saklolo.triage.Urgency
import ph.appbuilders.saklolo.ui.theme.ForestMint
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.InkSoft
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

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        askedMic = true
        val locationOk = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationOk) viewModel.onLocationPermissionGranted()
        syncPermissions(context, askedMic = true) { blocked -> micBlocked = blocked }
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
                syncPermissions(context, askedMic) { blocked -> micBlocked = blocked }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!RelayPermissions.granted(context)) {
            permissions.launch(requiredPermissions())
        } else {
            syncPermissions(context, askedMic) { blocked -> micBlocked = blocked }
        }
    }

    val lastAlert = sos.sentAlertId?.let { id -> alerts.firstOrNull { it.id == id } }
        ?: alerts.firstOrNull { viewModel.clipReady(it) && it.hops == 0 }
        ?: alerts.firstOrNull { it.hops == 0 }

    Box(Modifier.fillMaxSize().background(Page)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                gemmaLoading = sos.gemmaLoading,
                onBack = {
                    if (route == RECORD) settingsOpen = true else route = RECORD
                },
                backDescription = if (route == RECORD) "Settings" else "Back",
            )
            ScreenHeading(route, alerts, sos)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (route == ASK) {
                    AskScreen(
                        turns = askTurns,
                        onAsk = viewModel::submitAsk,
                        onOpenRecorder = { route = RECORD },
                    )
                } else if (route == RECORD) {
                    SosScreen(
                        state = sos,
                        peers = peers,
                        lastAlert = if (sos.actionable) null else lastAlert,
                        clipReady = viewModel::clipReady,
                        onHoldStart = {
                            if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
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
                        micBlocked = micBlocked,
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
                    )
                }
            }
        }
        BottomSwitcher(
            route = route,
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
private fun TopBar(gemmaLoading: Boolean, onBack: () -> Unit, backDescription: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDescription, tint = Ink)
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ForestMint),
            )
            Text(
                text = if (gemmaLoading) "AI loading…" else "Offline",
                color = Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun ScreenHeading(route: String, alerts: List<Alert>, sos: SosUiState) {
    val title = when (route) {
        FEED -> "Alerts"
        ASK -> "Ask B-LINK"
        else -> "Send SOS"
    }
    val subtitle = when (route) {
        FEED -> feedSubtitle(alerts)
        ASK -> "Answers from official safety guides only"
        else -> "Hold the button and speak in Tagalog"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
    ) {
        Text(title, color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = InkSoft, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
        if (route == RECORD) {
            recordStatusLine(sos)?.let { line ->
                Text(line, color = InkSoft, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private fun feedSubtitle(alerts: List<Alert>): String {
    val critical = alerts.count { it.urgency == Urgency.CRITICAL }
    val help = alerts.count { it.urgency == Urgency.NEEDS_HELP }
    val safe = alerts.count { it.urgency == Urgency.SAFE }
    return "$critical critical · $help needs help · $safe safe"
}

private fun recordStatusLine(state: SosUiState): String? {
    state.error?.let { return it }
    val idle = "Hold the button and speak. Tagalog, Bisaya, or English."
    if (state.status != idle) return state.status
    if (!state.modelReady) return state.modelStatus
    return null
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

private fun syncPermissions(context: Context, askedMic: Boolean, onMicBlocked: (Boolean) -> Unit) {
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
