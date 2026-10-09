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
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ph.appbuilders.saklolo.SakloloViewModel
import ph.appbuilders.saklolo.contact.CallPhase
import ph.appbuilders.saklolo.relay.RelayPermissions
import ph.appbuilders.saklolo.relay.RelayService
import ph.appbuilders.saklolo.ui.theme.Ink

private const val CONTACTS = "contacts"
private const val MESSAGES = "messages"
private const val ADD = "add"
private const val FEED = "sos"
private const val THREAD = "thread"
private const val CALL = "call"

@Composable
fun SakloloApp(viewModel: SakloloViewModel) {
    val sos by viewModel.sos.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val messages by viewModel.directMessages.collectAsStateWithLifecycle()
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val call by viewModel.callUi.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var route by remember { mutableStateOf(CONTACTS) }
    var peerId by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("all") }
    var draft by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var quickCall by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf(viewModel.displayName()) }
    var askName by remember { mutableStateOf(viewModel.needsNamePrompt()) }
    var settingsOpen by remember { mutableStateOf(false) }
    var askedBattery by remember { mutableStateOf(false) }
    var askedMic by remember { mutableStateOf(false) }
    var micBlocked by remember { mutableStateOf(false) }
    var askedCamera by remember { mutableStateOf(false) }
    var cameraBlocked by remember { mutableStateOf(false) }
    var askedLocation by remember { mutableStateOf(false) }
    var locationWarning by remember { mutableStateOf<String?>(null) }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        askedMic = true
        askedLocation = true
        askedCamera = true
        cameraBlocked = !hasPermission(context, Manifest.permission.CAMERA) &&
            cameraIsPermanentlyDenied(context)
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
                if (hasPermission(context, Manifest.permission.CAMERA)) {
                    cameraBlocked = false
                } else if (askedCamera && cameraIsPermanentlyDenied(context)) {
                    cameraBlocked = true
                }
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

    val unread = threads.sumOf { it.unread }
    val now = System.currentTimeMillis()
    val openRow = contacts.firstOrNull { it.deviceId == peerId }
    val threadMessages = messages.filter { message ->
        val other = if (viewModel.isMine(message)) message.toDeviceId else message.fromDeviceId
        other == peerId && message.kind in setOf("text", "voice", "ping", "call_clip")
    }
    val qrPayload = viewModel.myQr()
    val qrImage by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, qrPayload) {
        value = withContext(Dispatchers.Default) { qrBitmap(qrPayload).asImageBitmap() }
    }
    LaunchedEffect(nameDraft) {
        delay(400)
        if (nameDraft != viewModel.displayName()) viewModel.setDisplayName(nameDraft)
    }
    LaunchedEffect(route, peerId, openRow) {
        if (route == THREAD && peerId != null) viewModel.markThreadRead(peerId!!)
        if (route == THREAD && openRow == null) route = CONTACTS
    }
    LaunchedEffect(call.phase) {
        if (call.phase == CallPhase.INCOMING || call.phase == CallPhase.OUTGOING || call.phase == CallPhase.ACTIVE) {
            if (route != CALL) route = CALL
        }
    }
    BackHandler(enabled = askName || quickCall || route != CONTACTS) {
        when {
            askName -> Unit
            quickCall -> quickCall = false
            route == CALL -> {
                viewModel.endCall()
                route = if (peerId != null) THREAD else CONTACTS
            }
            else -> route = CONTACTS
        }
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
            cameraBlocked = false
            scanner.launch(scanOptions())
        } else if (askedCamera && cameraIsPermanentlyDenied(context)) {
            cameraBlocked = true
        } else {
            permissions.launch(requiredPermissions())
        }
    }

    fun openThread(id: String) {
        peerId = id
        draft = ""
        quickCall = false
        route = THREAD
    }

    fun place(id: String) {
        val row = contacts.firstOrNull { it.deviceId == id }
        if (row == null || !row.inRange) {
            viewModel.placeCall(id)
            return
        }
        peerId = id
        quickCall = false
        viewModel.placeCall(id)
        route = CALL
    }

    V17Scaffold(
        route = when (route) {
            MESSAGES -> "messages"
            ADD -> "add"
            FEED -> "sos"
            else -> "contacts"
        },
        unread = unread,
        showTabs = route != CALL,
        notice = notice,
        onDismissNotice = viewModel::clearNotice,
        onContacts = { route = CONTACTS },
        onMessages = { route = MESSAGES },
        onCall = { quickCall = true },
        onAdd = { route = ADD },
        onSos = { route = FEED },
        overlay = if (quickCall && route != CALL) {
            {
                V17QuickCall(
                    rows = contacts.filter { it.inRange },
                    onCall = { place(it.deviceId) },
                    onClose = { quickCall = false },
                )
            }
        } else {
            null
        },
    ) {
        when (route) {
            THREAD -> {
                val row = openRow
                if (row != null) {
                    V17Thread(
                        row = row,
                        messages = threadMessages,
                        mine = viewModel::isMine,
                        draft = draft,
                        onDraft = { draft = it },
                        onSend = {
                            viewModel.sendDirect(row.deviceId, draft)
                            draft = ""
                        },
                        onMic = {
                            if (voice.recording) viewModel.stopVoiceNote(sendAfter = true)
                            else ensureMic { viewModel.startVoiceNote(row.deviceId) }
                        },
                        recording = voice.recording,
                        onPing = { viewModel.sendPing(row.deviceId) },
                        onCall = { place(row.deviceId) },
                        onBack = { route = CONTACTS },
                        onFavorite = { viewModel.toggleFavorite(row.deviceId) },
                        onPlay = viewModel::playNote,
                        now = now,
                    )
                }
            }
            CALL -> V17InCall(
                call = call,
                inRange = contacts.firstOrNull { it.deviceId == call.peerId }?.inRange == true,
                onSos = viewModel::sendCallSos,
                onDismiss = viewModel::dismissEmergency,
                onHoldStart = { ensureMic { viewModel.startHold() } },
                onHoldEnd = viewModel::stopHold,
                onSpeaker = { viewModel.setSpeaker(!call.speakerOn) },
                onEnd = {
                    viewModel.endCall()
                    route = if (peerId != null) THREAD else CONTACTS
                },
                onAccept = viewModel::acceptCall,
                onDecline = {
                    viewModel.declineCall()
                    route = CONTACTS
                },
            )
            MESSAGES -> V17Messages(
                threads = threads,
                query = search,
                onQuery = { search = it },
                now = now,
                onOpen = { openThread(it.peerId) },
                onSos = { viewModel.sendSosText(it.criticalBody) },
            )
            ADD -> V17Add(
                name = nameDraft,
                code = viewModel.shortCode(),
                qr = qrImage,
                recent = contacts.filter { it.saved }.sortedByDescending { it.addedAtMillis },
                now = now,
                bluetooth = bluetoothOn(context),
                onName = { nameDraft = it.take(40) },
                onScan = { scanQr() },
                onShare = { shareCode(context, qrPayload) },
                onCall = { place(it.deviceId) },
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
            else -> V17Contacts(
                rows = contacts,
                filter = filter,
                onFilter = { filter = it },
                now = now,
                onOpen = { openThread(it.deviceId) },
                onCall = { place(it.deviceId) },
                onScan = { scanQr() },
            )
        }
        if (micBlocked) {
            TextButton(onClick = { openAppSettings(context) }) {
                Text("Microphone is off. Open settings.", color = Ink)
            }
        }
        if (cameraBlocked) {
            Text(
                "Camera is off. Allow it to scan a contact QR.",
                color = Ink,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = { openAppSettings(context) }) {
                Text("Open settings", color = Ink)
            }
        }
        locationWarning?.let {
            Text(it, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }

    if (askName) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            NamePrompt(initial = viewModel.displayName()) { typed ->
                viewModel.confirmDisplayName(typed)
                nameDraft = viewModel.displayName()
                askName = false
            }
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

private fun shareCode(context: Context, payload: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("B-LINK", payload))
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, payload)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share my code")) }
}

private fun scanOptions(): ScanOptions = ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    setPrompt("Scan their contact QR")
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

private fun cameraIsPermanentlyDenied(context: Context): Boolean {
    val activity = context as? Activity ?: return false
    if (hasPermission(context, Manifest.permission.CAMERA)) return false
    return !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
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
