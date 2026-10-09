package ph.appbuilders.saklolo.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
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
import ph.appbuilders.saklolo.contact.ContactQr
import ph.appbuilders.saklolo.contact.DirectGate
import ph.appbuilders.saklolo.contact.QuickReplies
import ph.appbuilders.saklolo.contact.ReplyChannel
import ph.appbuilders.saklolo.contact.ReplySession
import ph.appbuilders.saklolo.relay.ReadyToConnect
import ph.appbuilders.saklolo.relay.RelayService
import ph.appbuilders.saklolo.relay.SetupFacts
import ph.appbuilders.saklolo.relay.SetupKey
import ph.appbuilders.saklolo.relay.SetupProbe
import ph.appbuilders.saklolo.ui.theme.Ink
import ph.appbuilders.saklolo.ui.theme.Page
import ph.appbuilders.saklolo.ui.theme.Poppins
import ph.appbuilders.saklolo.ui.theme.VioletDeep

private const val HOME = MainNav.HOME
private const val CONTACTS = MainNav.CONTACTS
private const val MESSAGES = MainNav.MESSAGES
private const val ADD = MainNav.ADD
private const val THREAD = MainNav.THREAD
private const val CALL = MainNav.CALL

@Composable
fun SakloloApp(viewModel: SakloloViewModel) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val messages by viewModel.directMessages.collectAsStateWithLifecycle()
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val call by viewModel.callUi.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var route by rememberSaveable { mutableStateOf(HOME) }
    var peerId by rememberSaveable { mutableStateOf("") }
    var backTab by rememberSaveable { mutableStateOf(HOME) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var draft by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var messageSearch by rememberSaveable { mutableStateOf("") }
    var quickCall by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf(viewModel.displayName()) }
    var askName by remember { mutableStateOf(viewModel.needsNamePrompt()) }
    var settingsOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var askedBattery by remember { mutableStateOf(false) }
    var askedMic by remember { mutableStateOf(false) }
    var micBlocked by remember { mutableStateOf(false) }
    var askedCamera by remember { mutableStateOf(false) }
    var cameraBlocked by remember { mutableStateOf(false) }
    var askedNearby by remember { mutableStateOf(false) }
    var askedNotifications by remember { mutableStateOf(false) }
    var pillTapped by remember { mutableStateOf(false) }
    var setupSeen by remember { mutableStateOf(viewModel.setupSeen()) }
    var facts by remember { mutableStateOf(decorateFacts(context, false, false, false, false)) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        facts = decorateFacts(context, askedNearby, askedMic, askedCamera, askedNotifications)
        startRelay(context)
    }
    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        askedMic = true
        askedCamera = true
        askedNearby = true
        cameraBlocked = !hasPermission(context, Manifest.permission.CAMERA) &&
            cameraIsPermanentlyDenied(context)
        val locationOk = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationOk) viewModel.onLocationPermissionGranted()
        facts = decorateFacts(context, askedNearby, askedMic, askedCamera, askedNotifications)
        startRelay(context)
        if (!askedBattery) {
            askedBattery = true
            (context as? Activity)?.let { askBatteryExemption(it) }
        }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::ingestQr)
    }

    DisposableEffect(lifecycleOwner, context, askedMic, askedNearby, askedCamera, askedNotifications) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val locationOk = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                    hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (locationOk) viewModel.onLocationPermissionGranted()
                facts = decorateFacts(context, askedNearby, askedMic, askedCamera, askedNotifications)
                startRelay(context)
                if (hasPermission(context, Manifest.permission.CAMERA)) {
                    cameraBlocked = false
                } else if (askedCamera && cameraIsPermanentlyDenied(context)) {
                    cameraBlocked = true
                }
                if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) micBlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context, askedNearby, askedMic, askedCamera, askedNotifications) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(incoming: Context?, intent: Intent?) {
                facts = decorateFacts(context, askedNearby, askedMic, askedCamera, askedNotifications)
                startRelay(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    LaunchedEffect(Unit) {
        facts = decorateFacts(context, askedNearby, askedMic, askedCamera, askedNotifications)
        startRelay(context)
    }

    val unread = threads.sumOf { it.unread }
    val now = System.currentTimeMillis()
    val openRow = contacts.firstOrNull { peerId.isNotEmpty() && it.deviceId == peerId }
    val threadMessages = messages.filter { message ->
        val other = if (viewModel.isMine(message)) message.toDeviceId else message.fromDeviceId
        other == peerId && message.kind in DirectGate.chatKinds
    }
    val modelReplies by viewModel.modelReplies.collectAsStateWithLifecycle()
    val threadReplies = remember { ReplySession() }
    val callReplies = remember { ReplySession() }
    val threadHeard = QuickReplies.latestHeard(threadMessages, viewModel.deviceId())
    val callHeard = call.captions.lastOrNull { !it.mine && !it.transcribing }?.text?.trim().orEmpty()
    LaunchedEffect(threadHeard) { viewModel.offerReplies(threadHeard) }
    LaunchedEffect(callHeard) { viewModel.offerReplies(callHeard) }
    fun chipsFor(scope: String, heard: String, session: ReplySession) = when (val showing = session.offer(scope, heard)) {
        "" -> emptyList()
        else -> if (QuickReplies.mayAskModel(showing)) {
            modelReplies[showing] ?: QuickReplies.fromRules(showing)
        } else {
            QuickReplies.fromRules(showing)
        }
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
        if (route == THREAD && peerId.isNotEmpty()) viewModel.markThreadRead(peerId)
        if (route == THREAD && openRow == null) route = if (backTab == THREAD) MESSAGES else backTab
    }
    LaunchedEffect(call.phase) {
        if (call.phase == CallPhase.INCOMING || call.phase == CallPhase.OUTGOING || call.phase == CallPhase.ACTIVE) {
            if (route != CALL) route = CALL
        }
    }
    val setupMissing = ReadyToConnect.requiredMissing(facts)
    val showSetup = ReadyToConnect.show(setupSeen, setupMissing, pillTapped)
    fun dismissSetup() {
        viewModel.markSetupSeen()
        setupSeen = true
        pillTapped = false
    }
    BackHandler(enabled = showSetup || askName || quickCall || route != HOME) {
        when {
            showSetup -> dismissSetup()
            askName -> Unit
            quickCall -> quickCall = false
            route == CALL -> {
                viewModel.endCall()
                route = backTab
            }
            route == THREAD -> route = backTab
            else -> route = HOME
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

    fun rememberTab() {
        if (KolNav.showsBar(route)) backTab = route
    }

    fun openThread(id: String) {
        rememberTab()
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
        rememberTab()
        peerId = id
        quickCall = false
        viewModel.placeCall(id)
        route = CALL
    }

    val scenes = rememberSaveableStateHolder()
    val selfName = nameDraft.ifBlank { viewModel.displayName() }
    Box(Modifier.fillMaxSize().background(Page)) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                val fromRight = KolNav.slideFromRight(initialState, targetState)
                val distance: (Int) -> Int = { full -> if (fromRight) full / 8 else -full / 8 }
                val exit: (Int) -> Int = { full -> if (fromRight) -full / 8 else full / 8 }
                (fadeIn(tween(KolNav.MOTION_MS)) + slideInHorizontally(tween(KolNav.MOTION_MS), distance)) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(KolNav.MOTION_MS), exit))
            },
            label = "kol",
        ) { target ->
            scenes.SaveableStateProvider(KolNav.stateKey(target, peerId)) {
                when (target) {
                    THREAD -> {
                        val row = openRow
                        if (row != null) {
                            KolChat(
                                row = row,
                                messages = threadMessages,
                                mine = viewModel::isMine,
                                draft = draft,
                                onDraft = { draft = it },
                                onSend = {
                                    val text = draft.trim()
                                    if (text.isEmpty()) return@KolChat
                                    threadReplies.replied(row.deviceId, threadHeard, ReplyChannel.TEXT)
                                    viewModel.sendDirect(row.deviceId, text)
                                    draft = ""
                                },
                                onMic = {
                                    if (voice.recording) viewModel.stopVoiceNote(sendAfter = true)
                                    else ensureMic {
                                        threadReplies.replied(row.deviceId, threadHeard, ReplyChannel.VOICE)
                                        viewModel.startVoiceNote(row.deviceId)
                                    }
                                },
                                recording = voice.recording,
                                onCall = { place(row.deviceId) },
                                onBack = { route = backTab },
                                onPlay = viewModel::playNote,
                                now = now,
                                chips = chipsFor(row.deviceId, threadHeard, threadReplies),
                                onChip = { chip ->
                                    if (chip.fill) draft = chip.sendText
                                    else {
                                        threadReplies.replied(row.deviceId, threadHeard, ReplyChannel.CHIP)
                                        viewModel.sendDirect(row.deviceId, chip.sendText)
                                    }
                                },
                            )
                        }
                    }
                    CALL -> KolCall(
                        call = call,
                        inRange = contacts.firstOrNull { it.deviceId == call.peerId }?.inRange == true,
                        onBack = {
                            viewModel.endCall()
                            route = backTab
                        },
                        onSpeaker = { viewModel.setSpeaker(!call.speakerOn) },
                        onUrgent = { viewModel.sendUrgent(call.peerId, call.emergency.orEmpty()) },
                        onDismiss = viewModel::dismissEmergency,
                        onHoldStart = {
                            ensureMic {
                                callReplies.replied(call.peerId, callHeard, ReplyChannel.VOICE)
                                viewModel.startHold()
                            }
                        },
                        onHoldEnd = viewModel::stopHold,
                        onMute = viewModel::setHoldMuted,
                        onEnd = {
                            viewModel.endCall()
                            route = backTab
                        },
                        onAccept = viewModel::acceptCall,
                        onDecline = {
                            viewModel.declineCall()
                            route = backTab
                        },
                        chips = chipsFor(call.peerId, callHeard, callReplies),
                        onChip = { chip ->
                            callReplies.replied(call.peerId, callHeard, ReplyChannel.CHIP)
                            viewModel.sendDirect(call.peerId, chip.sendText)
                        },
                        onSendText = { text ->
                            val trimmed = text.trim()
                            if (trimmed.isEmpty()) return@KolCall
                            callReplies.replied(call.peerId, callHeard, ReplyChannel.TEXT)
                            viewModel.sendDirect(call.peerId, trimmed)
                        },
                    )
                    MESSAGES -> KolMessages(
                        threads = threads,
                        query = messageSearch,
                        onQuery = { messageSearch = it },
                        now = now,
                        selfName = selfName,
                        onMenu = { aboutOpen = true },
                        onBell = { if (setupMissing > 0) pillTapped = true else route = MESSAGES },
                        onAvatar = { route = ADD },
                        bellDot = unread > 0 || setupMissing > 0,
                        onOpen = { openThread(it.peerId) },
                        onUrgent = { viewModel.sendUrgent(it.peerId, it.criticalBody) },
                    )
                    ADD -> KolAdd(
                        name = nameDraft,
                        onName = { nameDraft = it.take(40) },
                        code = viewModel.shortCode(),
                        qr = qrImage,
                        nearby = contacts.filter { it.inRange && !it.saved },
                        onMenu = { aboutOpen = true },
                        onBell = { if (setupMissing > 0) pillTapped = true },
                        onAvatar = { route = ADD },
                        bellDot = unread > 0 || setupMissing > 0,
                        onScan = { scanQr() },
                        onShare = { shareCode(context, qrPayload) },
                        onAdd = { row -> viewModel.ingestQr(ContactQr.encode(row.deviceId, row.name)) },
                    )
                    CONTACTS -> KolContacts(
                        rows = contacts,
                        filter = filter,
                        onFilter = { filter = it },
                        query = search,
                        onQuery = { search = it },
                        now = now,
                        selfName = selfName,
                        onMenu = { aboutOpen = true },
                        onBell = { if (setupMissing > 0) pillTapped = true },
                        onAvatar = { route = ADD },
                        bellDot = unread > 0 || setupMissing > 0,
                        onOpen = { openThread(it.deviceId) },
                        onCall = { place(it.deviceId) },
                        onFavorite = { viewModel.toggleFavorite(it.deviceId) },
                    )
                    else -> KolHome(
                        name = selfName,
                        rows = contacts,
                        messages = messages,
                        myId = viewModel.deviceId(),
                        setupMissing = setupMissing,
                        now = now,
                        onMenu = { aboutOpen = true },
                        onBell = { if (setupMissing > 0) pillTapped = true else if (unread > 0) route = MESSAGES },
                        onAvatar = { route = ADD },
                        bellDot = unread > 0 || setupMissing > 0,
                        onCallAny = {
                            val near = contacts.filter { it.inRange }
                            when (near.size) {
                                0 -> route = CONTACTS
                                1 -> place(near.first().deviceId)
                                else -> quickCall = true
                            }
                        },
                        onMessage = { route = MESSAGES },
                        onVoice = { id ->
                            val heard = QuickReplies.latestHeard(
                                messages.filter { message ->
                                    val other = if (viewModel.isMine(message)) message.toDeviceId else message.fromDeviceId
                                    other == id && message.kind in DirectGate.chatKinds
                                },
                                viewModel.deviceId(),
                            )
                            threadReplies.replied(id, heard, ReplyChannel.VOICE)
                            openThread(id)
                            ensureMic { viewModel.startVoiceNote(id) }
                        },
                        onAdd = { route = ADD },
                        onOpen = { openThread(it) },
                    )
                }
            }
        }
        val banner = notice
        if (banner != null) {
            Text(
                banner,
                color = VioletDeep,
                fontFamily = Poppins,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp).clickable { viewModel.clearNotice() },
            )
        }
        if (micBlocked) {
            TextButton(onClick = { openAppSettings(context) }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)) {
                Text("Microphone is off. Open settings.", color = Ink)
            }
        }
        if (cameraBlocked) {
            TextButton(onClick = { openAppSettings(context) }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)) {
                Text("Camera is off. Open settings.", color = Ink)
            }
        }
        if (quickCall && route != CALL) {
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 88.dp)) {
                KolQuickCall(
                    rows = contacts.filter { it.inRange },
                    onCall = { place(it.deviceId) },
                    onClose = { quickCall = false },
                )
            }
        }
        if (KolNav.showsBar(route)) {
            KolBar(
                route = route,
                unread = unread,
                onHome = { route = HOME },
                onContacts = { route = CONTACTS },
                onMessages = { route = MESSAGES },
                onAdd = { route = ADD },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp),
            )
        }
    }

    if (askName && !showSetup) {
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

    if (showSetup) {
        Dialog(
            onDismissRequest = { dismissSetup() },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            ReadyToConnectScreen(
                rows = ReadyToConnect.rows(facts),
                requiredReady = setupMissing == 0,
                onFix = { key ->
                    fixSetup(
                        context,
                        key,
                        facts,
                        settingsLauncher::launch,
                        permissions::launch,
                        onAskedNearby = { askedNearby = true },
                        onAskedMic = { askedMic = true },
                        onAskedCamera = { askedCamera = true },
                        onAskedNotifications = { askedNotifications = true },
                    )
                },
                onContinue = { dismissSetup() },
            )
        }
    }

    if (aboutOpen) {
        DisclosureDialog(onClose = { aboutOpen = false })
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
    clipboard?.setPrimaryClip(ClipData.newPlainText("KOL", payload))
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

private fun startRelay(context: Context) {
    try {
        ContextCompat.startForegroundService(context, Intent(context, RelayService::class.java))
    } catch (error: Exception) {
        android.util.Log.i(
            "BLINK",
            "startForegroundService failure ${error.javaClass.simpleName} message=${error.message}",
        )
    }
}

private fun decorateFacts(
    context: Context,
    askedNearby: Boolean,
    askedMic: Boolean,
    askedCamera: Boolean,
    askedNotifications: Boolean,
): SetupFacts {
    val base = SetupProbe.read(context)
    return base.copy(
        nearbyDenied = SetupProbe.permanentlyDenied(context, SetupProbe.missingNearby(context), askedNearby),
        microphoneDenied = SetupProbe.permanentlyDenied(
            context,
            listOf(Manifest.permission.RECORD_AUDIO),
            askedMic,
        ),
        cameraDenied = SetupProbe.permanentlyDenied(context, listOf(Manifest.permission.CAMERA), askedCamera),
        notificationsDenied = Build.VERSION.SDK_INT >= 33 && SetupProbe.permanentlyDenied(
            context,
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            askedNotifications,
        ),
    )
}

private fun fixSetup(
    context: Context,
    key: SetupKey,
    facts: SetupFacts,
    launch: (Intent) -> Unit,
    request: (Array<String>) -> Unit,
    onAskedNearby: () -> Unit,
    onAskedMic: () -> Unit,
    onAskedCamera: () -> Unit,
    onAskedNotifications: () -> Unit,
) {
    when (key) {
        SetupKey.BLUETOOTH -> {
            if (!facts.nearbyPermission) {
                onAskedNearby()
                val missing = SetupProbe.missingNearby(context)
                if (missing.isEmpty()) openAppSettings(context) else request(missing.toTypedArray())
            } else {
                launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }
        SetupKey.LOCATION -> launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        SetupKey.NEARBY -> {
            val missing = SetupProbe.missingNearby(context)
            if (facts.nearbyDenied || missing.isEmpty()) {
                openAppSettings(context)
            } else {
                onAskedNearby()
                request(missing.toTypedArray())
            }
        }
        SetupKey.WIFI -> {
            val panel = if (Build.VERSION.SDK_INT >= 29) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
            launch(panel)
        }
        SetupKey.MICROPHONE -> {
            if (facts.microphoneDenied) openAppSettings(context) else {
                onAskedMic()
                request(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        }
        SetupKey.CAMERA -> {
            if (facts.cameraDenied) openAppSettings(context) else {
                onAskedCamera()
                request(arrayOf(Manifest.permission.CAMERA))
            }
        }
        SetupKey.NOTIFICATIONS -> {
            if (Build.VERSION.SDK_INT < 33) return
            if (facts.notificationsDenied) openAppSettings(context) else {
                onAskedNotifications()
                request(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
        SetupKey.BATTERY -> launch(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
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
