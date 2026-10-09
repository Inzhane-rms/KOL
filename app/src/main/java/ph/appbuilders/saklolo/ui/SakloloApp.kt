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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ph.appbuilders.saklolo.SakloloViewModel
import ph.appbuilders.saklolo.group.recordIntent
import ph.appbuilders.saklolo.group.RecordIntent
import ph.appbuilders.saklolo.relay.RelayPermissions
import ph.appbuilders.saklolo.relay.RelayService
import ph.appbuilders.saklolo.ui.theme.Ink

private const val HOME = "home"
private const val CHAT = "chat"
private const val JOIN = "join"
private const val FIND = "find"
private const val FEED = "feed"

@Composable
fun SakloloApp(viewModel: SakloloViewModel) {
    val sos by viewModel.sos.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val groupNotes by viewModel.groupNotes.collectAsStateWithLifecycle()
    val sightings by viewModel.sightings.collectAsStateWithLifecycle()
    val activeGroup by viewModel.activeGroup.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var route by remember { mutableStateOf(HOME) }
    var settingsOpen by remember { mutableStateOf(false) }
    var askedBattery by remember { mutableStateOf(false) }
    var askedMic by remember { mutableStateOf(false) }
    var micBlocked by remember { mutableStateOf(false) }
    var askedLocation by remember { mutableStateOf(false) }
    var locationWarning by remember { mutableStateOf<String?>(null) }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        askedMic = true
        askedLocation = true
        val locationOk = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationOk) viewModel.onLocationPermissionGranted()
        syncPermissions(
            context,
            askedMic = true,
            askedLocation = true,
            onMicBlocked = { micBlocked = it },
            onMicGranted = {},
            onLocationWarning = { locationWarning = it },
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
                    askedLocation,
                    onMicBlocked = { micBlocked = it },
                    onMicGranted = {},
                    onLocationWarning = { locationWarning = it },
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val showLocationCard = RelayPermissions.needsPreciseChoice(context) ||
            (askedLocation && RelayPermissions.locationWarning(context) != null)
        if (RelayPermissions.granted(context) || showLocationCard) {
            syncPermissions(
                context,
                askedMic,
                askedLocation,
                onMicBlocked = { micBlocked = it },
                onMicGranted = {},
                onLocationWarning = { locationWarning = it },
            )
        } else {
            permissions.launch(requiredPermissions())
        }
    }

    val chatRead by viewModel.chatReadMillis.collectAsStateWithLifecycle()
    var displayName by remember { mutableStateOf(viewModel.displayName()) }
    val heard = sightings.filter { activeGroup == null || it.groupId == activeGroup?.id }
    val unread = groupNotes.count { note ->
        val groupId = activeGroup?.id
        groupId != null &&
            note.groupId == groupId &&
            note.sender != displayName &&
            note.kind != "ping" &&
            note.createdAtMillis > chatRead
    }
    val showSheet = voice.recording || voice.transcript.isNotBlank() || voice.status.isNotBlank()
    LaunchedEffect(route) {
        if (route == CHAT) viewModel.markChatRead()
    }

    fun ensureMic(onReady: () -> Unit) {
        if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
            micBlocked = false
            onReady()
        } else if (micIsPermanentlyDenied(context)) {
            askedMic = true
            micBlocked = true
        } else {
            permissions.launch(requiredPermissions())
        }
    }

    fun scanQr() {
        if (hasPermission(context, Manifest.permission.CAMERA)) {
            scanner.launch(scanOptions())
        } else {
            permissions.launch(requiredPermissions())
        }
    }

    V16Frame(
        peers = peers.size,
        route = when (route) {
            CHAT, JOIN -> CHAT
            FIND -> FIND
            FEED -> FEED
            else -> HOME
        },
        unread = unread,
        recording = voice.recording,
        notice = notice,
        onDismissNotice = viewModel::clearNotice,
        onHome = { route = HOME },
        onChat = { route = if (activeGroup == null) JOIN else CHAT },
        onRecord = {
            if (voice.recording) {
                viewModel.stopVoiceNote(sendAfter = false)
            } else when (recordIntent(groups.size)) {
                RecordIntent.OpenQrJoin -> route = JOIN
                RecordIntent.RecordVoice -> ensureMic { viewModel.startVoiceNote() }
            }
        },
        onFind = { route = FIND },
        onSos = { route = FEED },
        sheet = if (showSheet) {
            {
                V16RecordSheet(
                    groupName = activeGroup?.name ?: "Barkada",
                    voice = voice,
                    members = heard.size,
                    onCancel = viewModel::cancelVoiceNote,
                    onSend = viewModel::sendPendingVoice,
                )
            }
        } else {
            null
        },
    ) {
        when (route) {
            CHAT -> V16Chat(
                group = activeGroup,
                notes = groupNotes,
                displayName = displayName,
                memberCount = heard.size,
                onSend = viewModel::sendGroupText,
                onPlay = viewModel::playNote,
                onSendToMedics = viewModel::sendNoteToMedics,
            )
            JOIN -> V16Join(
                group = activeGroup,
                memberCount = heard.size,
                displayName = displayName,
                onDisplayName = { name ->
                    displayName = name
                    viewModel.setDisplayName(name)
                },
                onCreate = { name -> viewModel.createGroup(name) },
                onScan = { scanQr() },
            )
            FIND -> V16Find(
                sightings = heard,
                displayName = displayName,
                onPing = viewModel::pingGroup,
                onRefresh = viewModel::refreshGroups,
            )
            FEED -> V16Sos(
                sos = sos,
                alerts = alerts,
                onHoldStart = { ensureMic { viewModel.startRecording() } },
                onHoldEnd = { if (sos.recording) viewModel.stopRecording() },
                onHoldCancel = { if (sos.recording) viewModel.cancelRecording() },
                onUndo = viewModel::undoCancel,
                onSendDraft = viewModel::sendDraft,
                onDiscard = viewModel::discardDraft,
                onPlay = viewModel::playClip,
                onRespond = viewModel::markResponding,
                clipReady = viewModel::clipReady,
            )
            else -> V16Home(
                displayName = displayName,
                group = activeGroup,
                notes = groupNotes,
                sightings = sightings,
                onOpenChat = { route = if (activeGroup == null) JOIN else CHAT },
                onOpenJoin = { route = JOIN },
                onOpenFind = { route = FIND },
                onPing = viewModel::pingGroup,
                onOpenSos = { route = FEED },
                onPlay = viewModel::playNote,
                onSettings = { settingsOpen = true },
            )
        }
        if (micBlocked) {
            TextButton(onClick = { openAppSettings(context) }) {
                Text("Microphone is off. Open settings.", color = Ink)
            }
        }
        locationWarning?.let {
            Text(it, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
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
    askedLocation: Boolean,
    onMicBlocked: (Boolean) -> Unit,
    onMicGranted: (Boolean) -> Unit,
    onLocationWarning: (String?) -> Unit,
) {
    val warning = RelayPermissions.locationWarning(context)
    val showWarning = warning != null && (RelayPermissions.needsPreciseChoice(context) || askedLocation)
    onLocationWarning(if (showWarning) warning else null)
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
