package ph.appbuilders.saklolo

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ph.appbuilders.saklolo.contact.CaptionDisplay
import ph.appbuilders.saklolo.contact.CallMachine
import ph.appbuilders.saklolo.contact.CallPhase
import ph.appbuilders.saklolo.contact.CallSession
import ph.appbuilders.saklolo.contact.CallState
import ph.appbuilders.saklolo.contact.ClipPlay
import ph.appbuilders.saklolo.contact.Hangup
import ph.appbuilders.saklolo.contact.HoldMute
import ph.appbuilders.saklolo.contact.NameChoice
import ph.appbuilders.saklolo.contact.ContactQr
import ph.appbuilders.saklolo.contact.ContactRow
import ph.appbuilders.saklolo.contact.Conversation
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.QuickReplies
import ph.appbuilders.saklolo.contact.ReplyCache
import ph.appbuilders.saklolo.contact.ReplyChip
import ph.appbuilders.saklolo.contact.Identity
import ph.appbuilders.saklolo.contact.Ptt
import ph.appbuilders.saklolo.contact.RingLoop
import ph.appbuilders.saklolo.contact.ClipCommit
import ph.appbuilders.saklolo.contact.VoiceControl
import ph.appbuilders.saklolo.contact.WipeLaunch
import ph.appbuilders.saklolo.contact.WipeSessions
import ph.appbuilders.saklolo.ask.AskEngine
import ph.appbuilders.saklolo.ask.AskResult
import ph.appbuilders.saklolo.ask.AskTurn
import ph.appbuilders.saklolo.audio.ClipStore
import ph.appbuilders.saklolo.audio.Waveform
import ph.appbuilders.saklolo.audio.WavPcm
import ph.appbuilders.saklolo.contact.Urgent
import ph.appbuilders.saklolo.group.ConcertGroup
import ph.appbuilders.saklolo.group.GroupNote
import ph.appbuilders.saklolo.group.GroupQr
import ph.appbuilders.saklolo.group.GroupTriage
import ph.appbuilders.saklolo.group.Sighting
import ph.appbuilders.saklolo.group.VoiceDraft
import ph.appbuilders.saklolo.group.VoiceSheet
import ph.appbuilders.saklolo.location.DeviceLocation
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.relay.NearbyPeer
import ph.appbuilders.saklolo.relay.QrCodec
import ph.appbuilders.saklolo.stt.CaptionCleanup
import ph.appbuilders.saklolo.stt.ModelInstaller
import ph.appbuilders.saklolo.stt.PcmRecorder
import ph.appbuilders.saklolo.stt.SentenceCase
import ph.appbuilders.saklolo.stt.SpeechHearing
import ph.appbuilders.saklolo.stt.TranscriptLimit
import ph.appbuilders.saklolo.stt.SpeechLanguage
import ph.appbuilders.saklolo.stt.WhisperTranscriber
import ph.appbuilders.saklolo.summary.GemmaSummarizer
import ph.appbuilders.saklolo.ask.SafetyBank
import ph.appbuilders.saklolo.triage.SummaryRefine
import ph.appbuilders.saklolo.triage.TriageEngine
import ph.appbuilders.saklolo.triage.Urgency

data class SosUiState(
    val language: SpeechLanguage = SpeechLanguage.TAGALOG,
    val recording: Boolean = false,
    val elapsedSec: Int = 0,
    val modelReady: Boolean = false,
    val modelStatus: String = "Preparing the on-device speech model…",
    val status: String = "Hold the button and speak. Tagalog, Bisaya, or English.",
    val transcript: String = "",
    val summary: String = "",
    val urgency: Urgency? = null,
    val summarySource: String = SummaryRefine.RULES,
    val actionable: Boolean = false,
    val error: String? = null,
    val sentAlertId: String? = null,
    val gemmaStatus: String = "",
    val gemmaLoading: Boolean = false,
    val micLevel: Float = 0f,
    val canUndo: Boolean = false,
)

data class VoiceUiState(
    val recording: Boolean = false,
    val elapsedSec: Int = 0,
    val micLevel: Float = 0f,
    val status: String = "",
    val transcript: String = "",
)

data class CaptionLine(
    val id: String,
    val mine: Boolean,
    val speaker: String,
    val text: String,
    val transcribing: Boolean,
    val raw: String? = null,
)

data class CallUi(
    val phase: CallPhase = CallPhase.IDLE,
    val peerId: String = "",
    val peerName: String = "",
    val speakerOn: Boolean = false,
    val holding: Boolean = false,
    val elapsedSec: Int = 0,
    val playing: Boolean = false,
    val captions: List<CaptionLine> = emptyList(),
    val emergency: String? = null,
    val muted: Boolean = false,
    val callId: Long = 0L,
)

data class DemoConfig(
    val deviceName: String,
    val restrictPeers: Boolean,
    val allowlist: String,
    val language: SpeechLanguage,
    val gemmaStatus: String,
)

class SakloloViewModel(app: Application) : AndroidViewModel(app) {
    private val runtime = SakloloRuntime.get(app)
    private val store = runtime.store
    private val relay = runtime.relay
    private val recorder = PcmRecorder()
    private val recordGate = Mutex()
    private var transcriber: WhisperTranscriber? = null
    private var recordingStartedAt = 0L
    private var lastPcm: FloatArray? = null
    private var draftGeneration = 0
    @Volatile private var recordGeneration = 0
    private var undoGeneration = 0
    private var heldPcm: FloatArray? = null
    private var player: MediaPlayer? = null

    val alerts: StateFlow<List<Alert>> = runtime.alerts
    val groups: StateFlow<List<ConcertGroup>> = runtime.groups
    val groupNotes: StateFlow<List<GroupNote>> = runtime.groupNotes
    val sightings: StateFlow<List<Sighting>> = runtime.sightings
    val activeGroup: StateFlow<ConcertGroup?> = runtime.activeGroup
    val peers: StateFlow<List<NearbyPeer>> = runtime.peers
    val relayMessage: StateFlow<String> = runtime.relayMessage
    val contacts: StateFlow<List<ContactRow>> = runtime.contacts
    val directMessages: StateFlow<List<DirectMessage>> = runtime.directMessages

    private val replyCache = ReplyCache()
    private val _modelReplies = MutableStateFlow<Map<String, List<ReplyChip>>>(emptyMap())
    val modelReplies: StateFlow<Map<String, List<ReplyChip>>> = _modelReplies.asStateFlow()

    private val _voice = MutableStateFlow(VoiceUiState())
    val voice: StateFlow<VoiceUiState> = _voice.asStateFlow()
    private var pendingVoice: GroupNote? = null
    private var sendVoiceWhenReady = false
    @Volatile private var voiceEpoch = 0
    @Volatile private var voiceSession = 0
    private var voicePeer: String? = null
    private val threadRead = HashMap<String, Long>()
    private val playedClips = HashSet<String>()
    private val ring = RingLoop()
    private var call = CallState()
    private var callFocus: AudioFocusRequest? = null
    private val _threads = MutableStateFlow<List<Conversation>>(emptyList())
    val threads: StateFlow<List<Conversation>> = _threads.asStateFlow()
    private val _call = MutableStateFlow(CallUi())
    val callUi: StateFlow<CallUi> = _call.asStateFlow()
    private val _chatRead = MutableStateFlow(runtime.settings.lastChatReadMillis)
    val chatReadMillis: StateFlow<Long> = _chatRead.asStateFlow()

    private val _askTurns = MutableStateFlow<List<AskTurn>>(emptyList())
    val askTurns: StateFlow<List<AskTurn>> = _askTurns.asStateFlow()
    private var nextAskId = 1L
    private var askBank: SafetyBank? = null

    private val _sos = MutableStateFlow(SosUiState(language = runtime.settings.language))
    val sos: StateFlow<SosUiState> = _sos.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    private val _wipeEpoch = MutableStateFlow(0)
    val wipeEpoch: StateFlow<Int> = _wipeEpoch.asStateFlow()

    init {
        viewModelScope.launch {
            runtime.directMessages.collect {
                _threads.value = runtime.directStore.conversations(runtime.settings.deviceId, threadRead.toMap())
                absorbSignals(it)
            }
        }
        viewModelScope.launch {
            GemmaSummarizer.loading.collect { loading ->
                _sos.update { it.copy(gemmaLoading = loading) }
            }
        }
        viewModelScope.launch {
            try {
                val model = ModelInstaller.ensure(app) { progress ->
                    _sos.update { it.copy(modelStatus = progress) }
                }
                val speech = ModelInstaller.resolve(app).takeIf { it.exists() } ?: model
                transcriber = WhisperTranscriber(speech, app.getSharedPreferences("saklolo_decode", Context.MODE_PRIVATE))
                _sos.update {
                    it.copy(
                        modelReady = true,
                        modelStatus = "Speech model ready on this phone",
                        gemmaStatus = GemmaSummarizer.describe(app),
                    )
                }
            } catch (error: Exception) {
                _sos.update {
                    it.copy(
                        modelReady = false,
                        modelStatus = "Speech model unavailable",
                        error = error.message,
                        gemmaStatus = GemmaSummarizer.describe(app),
                    )
                }
            }
        }
    }

    fun demoConfig(): DemoConfig = DemoConfig(
        deviceName = runtime.settings.deviceName,
        restrictPeers = runtime.settings.restrictPeers,
        allowlist = runtime.settings.allowlistRaw,
        language = _sos.value.language,
        gemmaStatus = GemmaSummarizer.describe(getApplication()),
    )

    fun applyDemo(name: String, restrict: Boolean, allowlist: String, language: SpeechLanguage) {
        runtime.settings.language = language
        runtime.applyDemo(name, restrict, allowlist)
        _sos.update { it.copy(language = language, gemmaStatus = GemmaSummarizer.describe(getApplication())) }
    }

    fun setLanguage(language: SpeechLanguage) {
        runtime.settings.language = language
        _sos.update { it.copy(language = language) }
    }

    fun submitAsk(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val bank = safetyBank()
        val rules = AskEngine.answer(trimmed, bank)
        val id = nextAskId
        nextAskId += 1
        _askTurns.update { it + AskTurn(id, trimmed, rules) }
        val app = getApplication<Application>()
        if (rules !is AskResult.Fallback || !GemmaSummarizer.mightRun(app)) return
        val catalog = bank.pairs.joinToString("\n") { pair ->
            "${pair.id}. ${pair.questionTl} / ${pair.questionEn}"
        }
        val valid = bank.pairs.map { it.id }.toSet()
        viewModelScope.launch {
            val chosen = withContext(Dispatchers.IO) {
                GemmaSummarizer.chooseAskPair(app, trimmed, catalog, valid)
            } ?: return@launch
            val pair = bank.pairs.firstOrNull { it.id == chosen } ?: return@launch
            val tip = AskEngine.tipFor(pair).copy(fromModel = true)
            _askTurns.update { turns ->
                turns.map { turn ->
                    if (turn.id == id && turn.result is AskResult.Fallback) turn.copy(result = tip) else turn
                }
            }
        }
    }

    private fun safetyBank(): SafetyBank {
        askBank?.let { return it }
        val json = getApplication<Application>().assets.open("ask/ask_blink_qa.json")
            .bufferedReader()
            .use { it.readText() }
        return AskEngine.parse(json).also { askBank = it }
    }

    fun onTranscriptChange(text: String, raw: String? = null) {
        draftGeneration++
        val shown = TriageEngine.triage(text)
        val triage = if (raw.isNullOrBlank() || raw.trim() == text.trim()) {
            shown
        } else {
            SpeechHearing.higher(shown, TriageEngine.triage(raw))
        }
        _sos.update {
            it.copy(
                transcript = text,
                summary = if (triage.actionable) triage.summary else "",
                urgency = if (triage.actionable) triage.urgency else null,
                summarySource = SummaryRefine.RULES,
                actionable = triage.actionable,
                sentAlertId = null,
                error = null,
                status = if (triage.actionable) "Review, then send" else "Type or record an SOS",
            )
        }
    }

    fun startRecording() {
        if (_sos.value.recording || _voice.value.recording) return
        if (!_sos.value.modelReady) {
            _sos.update { it.copy(error = "The speech model is not ready yet.") }
            return
        }
        val failure = recorder.start()
        if (failure != null) {
            _sos.update { it.copy(error = failure, status = "Could not record") }
            return
        }
        heldPcm = null
        undoGeneration += 1
        recordingStartedAt = System.currentTimeMillis()
        recordGeneration += 1
        val generation = recordGeneration
        _sos.update {
            it.copy(
                recording = true,
                elapsedSec = 0,
                micLevel = 0f,
                error = null,
                sentAlertId = null,
                canUndo = false,
                status = "Listening… speak the SOS",
            )
        }
        viewModelScope.launch {
            while (recorder.isRunning && _sos.value.recording && generation == recordGeneration) {
                delay(250)
                if (generation != recordGeneration) return@launch
                val elapsed = ((System.currentTimeMillis() - recordingStartedAt) / 1000).toInt()
                val level = recorder.recentPeak()
                _sos.update { state -> state.copy(elapsedSec = elapsed, micLevel = level) }
                if (generation == recordGeneration && (elapsed >= PcmRecorder.MAX_SECONDS || !recorder.isRunning)) {
                    stopRecording()
                    break
                }
            }
        }
    }

    /** A far slide or a system cancel keeps the clip until [ph.appbuilders.saklolo.ui.CANCEL_UNDO_MS]. */
    fun cancelRecording() {
        recordGeneration += 1
        undoGeneration += 1
        val ticket = undoGeneration
        val generation = recordGeneration
        viewModelScope.launch {
            val pcm = recordGate.withLock {
                if (generation != recordGeneration || !recorder.isRunning) return@withLock null
                runtime.relay.onLocalRecordingFinished()
                recorder.stop()
            } ?: return@launch
            if (ticket != undoGeneration) return@launch
            heldPcm = pcm
            _sos.update {
                it.copy(
                    recording = false,
                    elapsedSec = 0,
                    micLevel = 0f,
                    canUndo = true,
                    status = "Cancelled · Undo",
                    error = null,
                )
            }
            delay(ph.appbuilders.saklolo.ui.CANCEL_UNDO_MS)
            if (ticket != undoGeneration) return@launch
            heldPcm = null
            _sos.update { state ->
                if (!state.canUndo) state else state.copy(
                    canUndo = false,
                    status = "Hold the button and speak. Tagalog, Bisaya, or English.",
                )
            }
        }
    }

    /** Restore the cancelled clip and transcribe it the same way a release does. */
    fun undoCancel() {
        val pcm = heldPcm ?: return
        heldPcm = null
        undoGeneration += 1
        viewModelScope.launch {
            try {
                _sos.update { it.copy(canUndo = false, recording = false, status = "Transcribing on this phone…", error = null) }
                finishRecording(pcm)
            } catch (error: Exception) {
                _sos.update {
                    it.copy(status = "Transcription failed", error = error.message ?: "Unknown error")
                }
            }
        }
    }

    fun stopRecording() {
        heldPcm = null
        undoGeneration += 1
        val generation = recordGeneration
        viewModelScope.launch {
            val pcm = recordGate.withLock {
                if (generation != recordGeneration) return@withLock null
                if (!_sos.value.recording && !recorder.isRunning) return@withLock null
                runtime.relay.onLocalRecordingFinished()
                _sos.update {
                    it.copy(recording = false, canUndo = false, micLevel = 0f, status = "Transcribing on this phone…", error = null)
                }
                recorder.stop()
            } ?: return@launch
            try {
                finishRecording(pcm)
            } catch (error: Exception) {
                _sos.update {
                    it.copy(status = "Transcription failed", error = error.message ?: "Unknown error")
                }
            }
        }
    }

    private suspend fun finishRecording(pcm: FloatArray) {
        if (pcm.size < PcmRecorder.SAMPLE_RATE / 2) {
            _sos.update {
                it.copy(
                    canUndo = false,
                    status = "Recording was too short",
                    error = "Hold the button a little longer and speak clearly.",
                )
            }
            return
        }
        val peak = pcm.maxOf { abs(it) }
        if (peak < 0.01f) {
            _sos.update {
                it.copy(
                    canUndo = false,
                    status = "Too quiet",
                    error = "The mic barely heard anything. Move closer and try again.",
                )
            }
            return
        }
        val engine = transcriber ?: error("Speech model is not loaded")
        val heard = hear(engine, pcm)
        if (heard.raw.isBlank()) {
            _sos.update {
                it.copy(
                    canUndo = false,
                    status = "No speech recognized",
                    error = "Try again, or type the SOS below.",
                )
            }
            return
        }
        lastPcm = pcm
        onTranscriptChange(heard.shown, heard.raw)
        refineWithGemma(heard.shown, draftGeneration)
    }

    private fun refineWithGemma(transcript: String, generation: Int) {
        val app = getApplication<Application>()
        if (!GemmaSummarizer.mightRun(app)) return
        val willInfer = GemmaSummarizer.isReady()
        viewModelScope.launch {
            _sos.update { state ->
                if (willInfer && state.actionable) state.copy(status = "Summarizing on this phone…") else state
            }
            val rules = _sos.value.summary
            if (rules.isBlank()) return@launch
            val chosen = withContext(Dispatchers.IO) {
                GemmaSummarizer.refine(app, transcript, rules)
            }
            _sos.update { state ->
                if (generation != draftGeneration || state.transcript.trim() != transcript.trim()) return@update state
                state.copy(
                    summary = chosen.summary,
                    summarySource = chosen.source,
                    status = "Review, then send",
                    gemmaStatus = GemmaSummarizer.describe(app),
                )
            }
        }
    }

    fun sendDraft() {
        val state = _sos.value
        if (!state.actionable || state.urgency == null) return
        val location = DeviceLocation.lastKnown(getApplication())
        val id = java.util.UUID.randomUUID().toString()
        val samples = lastPcm
        lastPcm = null
        viewModelScope.launch(Dispatchers.IO) {
            val audioPath = samples?.let { ClipStore.write(runtime.clipsDir, id, it) }
            publishDraft(id, state, location, audioPath)
        }
        return
    }

    private fun publishDraft(
        id: String,
        state: SosUiState,
        location: Pair<Double, Double>?,
        audioPath: String?,
    ) {
        val alert = Alert(
            id = id,
            transcript = state.transcript.trim().take(800),
            summary = state.summary.take(180),
            urgency = state.urgency ?: return,
            createdAtMillis = System.currentTimeMillis(),
            lat = location?.first,
            lon = location?.second,
            hops = 0,
            language = state.language.name,
            summarySource = state.summarySource,
            audioPath = audioPath,
        )
        store.addLocal(alert)
        val delivered = relay.broadcast(alert)
        store.markDelivered(id, delivered)
        runtime.refreshAlerts()
        _sos.update {
            it.copy(
                transcript = "",
                summary = "",
                urgency = null,
                actionable = false,
                summarySource = SummaryRefine.RULES,
                status = "Alert is on this phone and queued for relay",
                sentAlertId = id,
                error = null,
            )
        }
    }

    fun discardDraft() {
        draftGeneration++
        lastPcm = null
        heldPcm = null
        undoGeneration += 1
        _sos.update {
            it.copy(
                transcript = "",
                summary = "",
                urgency = null,
                actionable = false,
                summarySource = SummaryRefine.RULES,
                status = "Hold the button and speak. Tagalog, Bisaya, or English.",
                error = null,
                sentAlertId = null,
                canUndo = false,
            )
        }
    }

    fun onLocationPermissionGranted() {
        DeviceLocation.start(getApplication())
    }

    val location: StateFlow<Pair<Double, Double>?> = DeviceLocation.fixes

    fun isLocalOrigin(id: String): Boolean = store.isLocalOrigin(id)

    fun markResponding(id: String) {
        store.setResponding(id, true)
        runtime.refreshAlerts()
    }

    fun displayName(): String = runtime.settings.displayName

    fun needsNamePrompt(): Boolean = NameChoice.show(runtime.settings.nameChosen)

    fun termsAccepted(): Boolean = runtime.settings.termsAccepted

    fun acceptTerms() {
        runtime.settings.termsAccepted = true
    }

    /** Clears local messages, contacts, clips, and preferences. The speech model file stays. */
    fun deleteAllData(onCleared: () -> Unit = {}) {
        val next = WipeSessions(holdSession, voiceEpoch, voiceSession, recordGeneration).bump()
        voiceEpoch = next.epoch
        voiceSession = next.voice
        recordGeneration = next.record
        holdSession = next.hold
        runtime.directStore.bumpEpoch()
        transcriber?.abort()
        runtime.relay.cancelOutbound()
        val ending = call.phase != CallPhase.IDLE
        player?.release()
        player = null
        holding = false
        holdElapsed = 0
        transcribingHold = false
        holdMuted = HoldMute.afterEnd()
        val endPayloads = if (ending) {
            syncClock()
            ring.end(runtime.directMessages.value, deviceId())
            applyRing()
            ring.drain()
        } else {
            emptyList()
        }
        if (!ending) {
            releaseCallAudio()
            noteCall(CallState())
        } else {
            releaseCallAudio()
            publishCall(emergency = null)
        }
        WipeLaunch.tryStop(recordGate) {
            if (recorder.isRunning) {
                runtime.relay.onLocalRecordingFinished()
                recorder.stop()
            }
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    WipeLaunch.finishCallThenStop(
                        callActive = endPayloads.isNotEmpty(),
                        sendEnd = {
                            for (out in endPayloads) {
                                val body = when (out.kind) {
                                    Ptt.END -> "End"
                                    Ptt.ACCEPT -> "Accept"
                                    else -> "Decline"
                                }
                                val message = DirectMessage(
                                    id = java.util.UUID.randomUUID().toString(),
                                    fromDeviceId = deviceId(),
                                    toDeviceId = out.peerId,
                                    senderName = displayName(),
                                    body = body,
                                    createdAtMillis = System.currentTimeMillis(),
                                    kind = out.kind,
                                )
                                runtime.directStore.addLocal(message)
                                runtime.relay.broadcastDirect(message)
                            }
                        },
                        waitForSend = { runtime.relay.awaitOutbound(WipeLaunch.END_CALL_WINDOW_MS) },
                        stopRelay = {
                            runtime.wipeUserData {
                                replyCache.clear()
                                _modelReplies.value = emptyMap()
                            }
                            Waveform.clear()
                        },
                    )
                }
            } finally {
                threadRead.clear()
                _call.value = CallUi()
                _voice.value = VoiceUiState()
                _sos.update { it.copy(recording = false, elapsedSec = 0, transcript = "", summary = "", urgency = null) }
                _threads.value = emptyList()
                _wipeEpoch.value += 1
                _notice.value = null
                onCleared()
            }
        }
    }

    fun setupSeen(): Boolean = runtime.settings.setupSeen

    fun markSetupSeen() {
        runtime.settings.setupSeen = true
    }

    fun confirmDisplayName(name: String) {
        val (id, chosen) = NameChoice.saved(deviceId(), name, displayName())
        runtime.settings.displayName = chosen
        runtime.settings.nameChosen = true
        runtime.ensureRelay()
        check(deviceId() == id)
    }

    fun setDisplayName(name: String) {
        runtime.settings.displayName = name
        runtime.ensureRelay()
    }

    fun deviceId(): String = runtime.settings.deviceId

    fun myQr(): String = ContactQr.encode(deviceId(), displayName())

    fun shortCode(): String = Identity.shortCode(deviceId())

    fun isMine(message: DirectMessage): Boolean =
        Identity.isMine(message.fromDeviceId, deviceId(), message.localOrigin)

    fun createGroup(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val id = GroupQr.newId()
        runtime.groupStore.join(id, trimmed, System.currentTimeMillis(), createdHere = true)
        runtime.refreshGroups()
        return GroupQr.encode(id, trimmed)
    }

    fun selectGroup(id: String) {
        runtime.groupStore.setActive(id)
        runtime.refreshGroups()
    }

    fun sendGroupText(body: String) {
        val trimmed = body.trim()
        val group = runtime.groupStore.active() ?: return
        if (trimmed.isEmpty()) return
        val location = DeviceLocation.lastKnown(getApplication())
        val note = GroupNote(
            id = java.util.UUID.randomUUID().toString(),
            groupId = group.id,
            sender = runtime.settings.displayName,
            body = trimmed.take(800),
            createdAtMillis = System.currentTimeMillis(),
            lat = location?.first,
            lon = location?.second,
            urgency = GroupTriage.label(trimmed),
            kind = "text",
        )
        runtime.groupStore.addLocal(note)
        runtime.relay.broadcastNote(note)
        runtime.refreshGroups()
    }

    fun startVoiceNote(peerId: String? = null) {
        if (peerId == null && runtime.groupStore.groups().isEmpty()) return
        voicePeer = peerId
        if (_sos.value.recording || _voice.value.recording) return
        if (!_sos.value.modelReady) {
            _voice.update { it.copy(status = "The speech model is not ready yet.") }
            return
        }
        voiceSession += 1
        val failure = recorder.start()
        if (failure != null) {
            _voice.update { it.copy(status = failure) }
            return
        }
        heldPcm = null
        undoGeneration += 1
        recordingStartedAt = System.currentTimeMillis()
        recordGeneration += 1
        val generation = recordGeneration
        _voice.update { it.copy(recording = true, elapsedSec = 0, micLevel = 0f, status = "", transcript = "") }
        viewModelScope.launch {
            while (recorder.isRunning && _voice.value.recording && generation == recordGeneration) {
                delay(250)
                if (generation != recordGeneration) return@launch
                val elapsed = ((System.currentTimeMillis() - recordingStartedAt) / 1000).toInt()
                _voice.update { it.copy(elapsedSec = elapsed, micLevel = recorder.recentPeak()) }
                if (generation == recordGeneration && (elapsed >= PcmRecorder.MAX_SECONDS || !recorder.isRunning)) {
                    stopVoiceNote(sendAfter = voicePeer != null)
                    break
                }
            }
        }
    }

    fun stopVoiceNote(sendAfter: Boolean = false) {
        if (sendAfter) sendVoiceWhenReady = true
        val epoch = voiceEpoch
        val session = voiceSession
        viewModelScope.launch {
            val pcm = recordGate.withLock {
                if (!VoiceControl.shouldStopRecorder(session, voiceSession, recorder.isRunning) &&
                    !_voice.value.recording
                ) {
                    if (sendVoiceWhenReady && epoch == voiceEpoch && session == voiceSession) {
                        FloatArray(0)
                    } else {
                        null
                    }
                } else if (!recorder.isRunning) {
                    null
                } else {
                    runtime.relay.onLocalRecordingFinished()
                    if (session == voiceSession) {
                        _voice.update { it.copy(recording = false, status = "Transcribing…", transcript = "") }
                    }
                    recorder.stop()
                }
            }
            if (pcm == null) return@launch
            if (pcm.isEmpty()) {
                if (sendVoiceWhenReady && epoch == voiceEpoch && session == voiceSession) commitPendingVoice()
                return@launch
            }
            try {
                finishVoice(pcm, epoch, session)
            } catch (error: Exception) {
                if (session == voiceSession) {
                    sendVoiceWhenReady = false
                    _voice.update { it.copy(recording = false, status = error.message ?: "Transcription failed") }
                }
            }
        }
    }

    fun sendPendingVoice() {
        if (_voice.value.recording || recorder.isRunning) {
            stopVoiceNote(sendAfter = true)
            return
        }
        if (_voice.value.status == "Transcribing…") {
            sendVoiceWhenReady = true
            return
        }
        commitPendingVoice()
    }

    fun cancelVoiceNote() {
        sendVoiceWhenReady = false
        voiceEpoch += 1
        recordGeneration += 1
        val session = voiceSession
        val clip = pendingVoice?.audioPath
        pendingVoice = null
        if (!clip.isNullOrBlank()) File(clip).delete()
        val current = ph.appbuilders.saklolo.group.VoiceSheet(
            session = voiceSession,
            recording = _voice.value.recording,
            status = _voice.value.status,
            transcript = _voice.value.transcript,
        )
        val next = ph.appbuilders.saklolo.group.VoiceDraft.afterDiscard(session, current)
        if (next.session == current.session && next.transcript.isEmpty() && !next.recording && next.status.isEmpty()) {
            _voice.value = VoiceUiState()
        }
        viewModelScope.launch {
            recordGate.withLock {
                if (VoiceControl.shouldStopRecorder(session, voiceSession, recorder.isRunning)) {
                    runtime.relay.onLocalRecordingFinished()
                    recorder.stop()
                }
            }
        }
    }

    fun markChatRead() {
        val now = System.currentTimeMillis()
        runtime.settings.lastChatReadMillis = now
        _chatRead.value = now
    }

    fun refreshGroups() {
        runtime.refreshGroups()
    }

    fun pingGroup() {
        val group = runtime.groupStore.active() ?: return
        val location = DeviceLocation.lastKnown(getApplication())
        val note = GroupNote(
            id = java.util.UUID.randomUUID().toString(),
            groupId = group.id,
            sender = runtime.settings.displayName,
            body = "Ping",
            createdAtMillis = System.currentTimeMillis(),
            lat = location?.first,
            lon = location?.second,
            kind = "ping",
        )
        runtime.groupStore.addLocal(note)
        runtime.relay.broadcastNote(note)
        runtime.refreshGroups()
    }

    fun playNote(path: String?) {
        val file = path?.let { File(it) }
        if (file == null || !file.exists()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val next = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener { done ->
                        viewModelScope.launch(Dispatchers.Main) {
                            done.release()
                            if (player === done) player = null
                        }
                    }
                    prepare()
                }
                withContext(Dispatchers.Main) {
                    player?.release()
                    player = next
                    next.start()
                }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun finishVoice(pcm: FloatArray, epoch: Int, session: Int) {
        if (session != voiceSession || epoch != voiceEpoch) return
        if (pcm.size < PcmRecorder.SAMPLE_RATE / 2) {
            sendVoiceWhenReady = false
            _voice.update { it.copy(recording = false, status = "Recording was too short", transcript = "") }
            return
        }
        val engine = transcriber ?: run {
            sendVoiceWhenReady = false
            _voice.update { it.copy(recording = false, status = "The speech model is not ready yet.", transcript = "") }
            return
        }
        if (session != voiceSession) return
        _voice.update { it.copy(recording = false, status = "Transcribing…", transcript = "") }
        val heard = hear(engine, pcm)
        when (val decision = VoiceDraft.decide(epoch, voiceEpoch, heard.raw)) {
            is VoiceDraft.Finish.Discarded -> {
                discardVoiceSession(session)
                return
            }
            is VoiceDraft.Finish.Empty -> {
                if (session != voiceSession) return
                sendVoiceWhenReady = false
                _voice.update { it.copy(status = "No speech recognized", transcript = "") }
                return
            }
            is VoiceDraft.Finish.Keep -> {
                if (session != voiceSession) return
                val peer = voicePeer
                val location = DeviceLocation.lastKnown(getApplication())
                val id = java.util.UUID.randomUUID().toString()
                val audioPath = withContext(Dispatchers.IO) { ClipStore.write(runtime.clipsDir, id, pcm) }
                if (session != voiceSession ||
                    VoiceDraft.decide(epoch, voiceEpoch, decision.body) is VoiceDraft.Finish.Discarded
                ) {
                    audioPath?.let { File(it).delete() }
                    discardVoiceSession(session)
                    return
                }
                if (peer != null) {
                    if (session != voiceSession) {
                        audioPath?.let { File(it).delete() }
                        return
                    }
                    sendDirect(
                        peer,
                        heard.shown.take(800),
                        kind = "voice",
                        audioPath = audioPath,
                        rawBody = heard.raw.take(800),
                    )
                    voicePeer = null
                    _voice.value = VoiceUiState()
                    return
                }
                val group = runtime.groupStore.active() ?: run {
                    sendVoiceWhenReady = false
                    audioPath?.let { File(it).delete() }
                    return
                }
                pendingVoice = GroupNote(
                    id = id,
                    groupId = group.id,
                    sender = runtime.settings.displayName,
                    body = heard.shown.take(800),
                    createdAtMillis = System.currentTimeMillis(),
                    audioPath = audioPath,
                    lat = location?.first,
                    lon = location?.second,
                    urgency = heard.urgency.takeUnless { it == Urgency.SAFE },
                    kind = "voice",
                )
                if (session != voiceSession) {
                    pendingVoice = null
                    audioPath?.let { File(it).delete() }
                    return
                }
                _voice.update { it.copy(recording = false, status = "", transcript = pendingVoice?.body.orEmpty()) }
                if (sendVoiceWhenReady && epoch == voiceEpoch && session == voiceSession) {
                    sendVoiceWhenReady = false
                    commitPendingVoice()
                }
            }
        }
    }

    private fun discardVoiceSession(session: Int) {
        val current = VoiceSheet(
            session = voiceSession,
            recording = _voice.value.recording,
            status = _voice.value.status,
            transcript = _voice.value.transcript,
        )
        val next = VoiceDraft.afterDiscard(session, current)
        if (next == current) return
        sendVoiceWhenReady = false
        pendingVoice = null
        _voice.value = VoiceUiState()
    }

    private fun commitPendingVoice() {
        val note = pendingVoice ?: return
        pendingVoice = null
        runtime.groupStore.addLocal(note)
        runtime.relay.broadcastNote(note)
        runtime.refreshGroups()
        _voice.value = VoiceUiState()
    }

    fun sendNoteToMedics(noteId: String) {
        val note = runtime.groupStore.find(noteId) ?: return
        val alert = GroupTriage.sendToMedics(note, System.currentTimeMillis())
        store.addLocal(alert)
        val delivered = relay.broadcast(alert)
        store.markDelivered(alert.id, delivered)
        runtime.refreshAlerts()
        _notice.value = "Sent to medics"
    }

    fun ingestQr(payload: String) {
        val contact = ContactQr.decode(payload)
        if (contact != null) {
            if (contact.deviceId == deviceId()) {
                _notice.value = "That's your own code"
                return
            }
            viewModelScope.launch(Dispatchers.IO) {
                runtime.directStore.saveQr(contact.deviceId, contact.name, System.currentTimeMillis())
                runtime.refreshDirect()
                _notice.value = "Added ${contact.name}"
            }
            return
        }
        val alert = QrCodec.decode(payload)
        if (alert == null) {
            _notice.value = "That QR is not a KOL contact code"
            return
        }
        val fresh = store.ingest(listOf(alert))
        runtime.refreshAlerts()
        if (fresh.isEmpty()) {
            _notice.value = "This phone already has that alert"
        } else {
            fresh.forEach { relay.broadcast(it) }
            _notice.value = "Added from QR: ${fresh.first().summary}"
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun removeAlert(id: String) {
        store.find(id)?.audioPath?.let { path ->
            runCatching { File(path).delete() }
        }
        store.remove(id)
        runtime.refreshAlerts()
        if (_sos.value.sentAlertId == id) {
            _sos.update { it.copy(sentAlertId = null) }
        }
    }

    fun qrText(alert: Alert): String = QrCodec.encode(alert)

    fun clipReady(alert: Alert): Boolean {
        val path = alert.audioPath ?: return false
        val file = File(path)
        return file.exists() && file.length() > WavPcm.HEADER_BYTES
    }

    fun playClip(alert: Alert) {
        val path = alert.audioPath
        val file = path?.let { File(it) }
        if (file == null || !file.exists()) {
            _sos.update { it.copy(error = "That voice clip is not on this phone.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val next = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener { done ->
                        viewModelScope.launch(Dispatchers.Main) {
                            done.release()
                            if (player === done) player = null
                        }
                    }
                    prepare()
                }
                withContext(Dispatchers.Main) {
                    player?.release()
                    player = next
                    next.start()
                }
            } catch (error: Exception) {
                _sos.update { it.copy(error = error.message ?: "Could not play the voice clip") }
            }
        }
    }

    fun toggleFavorite(deviceId: String) {
        val row = runtime.directStore.rows().firstOrNull { it.deviceId == deviceId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runtime.directStore.setFavorite(deviceId, !row.favorite)
            runtime.refreshDirect()
        }
    }

    fun removeContact(deviceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runtime.directStore.remove(deviceId)
            runtime.refreshDirect()
            _notice.value = "Contact removed"
        }
    }

    fun markThreadRead(peerId: String) {
        threadRead[peerId] = System.currentTimeMillis()
        _threads.value = runtime.directStore.conversations(deviceId(), threadRead.toMap())
    }

    /** Rules show immediately. Gemma replaces them only when it is already loaded and the text parses. Emergencies stay on the rules. */
    fun offerReplies(heard: String) {
        val key = heard.trim()
        if (!QuickReplies.mayAskModel(key) || !GemmaSummarizer.isReady()) return
        viewModelScope.launch(Dispatchers.Default) {
            val raw = GemmaSummarizer.suggestReplies(key) ?: return@launch
            val chips = QuickReplies.acceptModel(raw) ?: return@launch
            _modelReplies.value = replyCache.put(key, chips)
        }
    }

    fun sendDirect(
        to: String,
        body: String,
        kind: String = "text",
        audioPath: String? = null,
        rawBody: String? = null,
    ) {
        val trimmed = body.trim()
        if (to.isBlank() || (trimmed.isEmpty() && kind == "text")) return
        val epoch = runtime.directStore.epoch()
        val message = DirectMessage(
            id = java.util.UUID.randomUUID().toString(),
            fromDeviceId = deviceId(),
            toDeviceId = to,
            senderName = displayName(),
            body = trimmed.ifEmpty { kind },
            createdAtMillis = System.currentTimeMillis(),
            kind = kind,
            audioPath = audioPath,
            rawBody = rawBody?.trim()?.ifEmpty { null },
        )
        viewModelScope.launch(Dispatchers.IO) {
            if (runtime.directStore.addIfCurrent(message, epoch) == null) {
                audioPath?.let { File(it).delete() }
                return@launch
            }
            runtime.relay.broadcastDirect(message)
            runtime.refreshDirect()
        }
    }

    fun sendPing(peerId: String) {
        sendDirect(peerId, "Ping", kind = "ping")
    }

    fun placeCall(peerId: String) {
        val row = runtime.directStore.rows().firstOrNull { it.deviceId == peerId } ?: return
        if (!row.inRange) {
            _notice.value = "Not in range right now"
            return
        }
        holdMuted = HoldMute.keep(holdMuted)
        syncClock()
        ring.inviteOut(peerId, row.name)
        applyRing()
        routeCallAudio(speaker = false)
        sendDirect(peerId, "Call", kind = Ptt.INVITE)
        watchRing()
        publishCall(emergency = null)
    }

    fun acceptCall() {
        if (call.peerId.isBlank()) return
        holdMuted = HoldMute.keep(holdMuted)
        syncClock()
        ring.accept(runtime.directMessages.value, deviceId())
        applyRing()
        flushRingSends()
        routeCallAudio(speaker = false)
        publishCall(emergency = null)
    }

    fun declineCall() {
        val holdingNow = _call.value.holding
        syncClock()
        ring.decline(runtime.directMessages.value, deviceId())
        applyRing()
        flushRingSends()
        finishHoldOnEnd(holdingNow)
        releaseCallAudio()
        publishCall(emergency = null)
    }

    fun endCall() {
        val holdingNow = _call.value.holding
        syncClock()
        ring.end(runtime.directMessages.value, deviceId())
        applyRing()
        flushRingSends()
        finishHoldOnEnd(holdingNow)
        releaseCallAudio()
        publishCall(emergency = null)
    }

    fun setSpeaker(on: Boolean) {
        if (call.phase != CallPhase.ACTIVE && call.phase != CallPhase.OUTGOING) return
        routeCallAudio(on)
        publishCall()
    }

    fun dismissEmergency() {
        runtime.directStore.thread(deviceId(), call.peerId)
            .filter { it.kind == Ptt.CLIP && Ptt.either(it.body, it.rawBody) }
            .forEach { dismissedEmergency += it.id }
        publishCall(emergency = null)
    }

    /** Possible-emergency action: an urgent message to this contact, not a broadcast alert. */
    fun sendUrgent(peerId: String, body: String) {
        val words = body.trim()
        if (peerId.isBlank() || words.isEmpty()) return
        sendDirect(peerId, words.take(800), kind = Urgent.KIND)
        if (call.peerId == peerId) dismissEmergency()
    }

    fun setHoldMuted(muted: Boolean) {
        holdMuted = muted
        if (HoldMute.dropInFlight(muted)) discardHold()
        publishCall()
    }

    fun startHold() {
        if (!HoldMute.allowStart(holdMuted)) return
        if (!CallMachine.canHold(call)) return
        if (_sos.value.recording || _voice.value.recording || recorder.isRunning) return
        if (!_sos.value.modelReady) {
            _notice.value = "The speech model is not ready yet."
            return
        }
        holdSession += 1
        val session = holdSession
        val failure = recorder.start()
        if (failure != null) {
            _notice.value = failure
            return
        }
        holdStartedAt = System.currentTimeMillis()
        holding = true
        transcribingHold = false
        publishCall()
        viewModelScope.launch {
            while (recorder.isRunning && holding && session == holdSession) {
                delay(200)
                holdElapsed = ((System.currentTimeMillis() - holdStartedAt) / 1000).toInt()
                publishCall()
                if (holdElapsed >= Ptt.MAX_SECONDS) {
                    stopHold()
                    break
                }
            }
        }
    }

    fun stopHold() {
        if (holdMuted) {
            discardHold()
            return
        }
        if (!holding && !recorder.isRunning) return
        val session = holdSession
        val epoch = runtime.directStore.epoch()
        val peer = call.peerId
        holding = false
        holdElapsed = 0
        publishCall()
        viewModelScope.launch {
            val pcm = recordGate.withLock {
                if (session != holdSession) return@withLock null
                if (!recorder.isRunning) return@withLock null
                runtime.relay.onLocalRecordingFinished()
                recorder.stop()
            } ?: return@launch
            transcribingHold = true
            publishCall()
            val engine = transcriber
            val heard = if (engine == null || pcm.size < PcmRecorder.SAMPLE_RATE / 2) {
                SpeechHearing.interpret("")
            } else {
                hear(engine, pcm)
            }
            if (!ClipCommit.allow(session, holdSession) || holdMuted) return@launch
            transcribingHold = false
            val body = heard.shown.trim()
            val raw = heard.raw.trim()
            if (body.isNotEmpty() && peer.isNotBlank()) {
                val id = java.util.UUID.randomUUID().toString()
                val audioPath = withContext(Dispatchers.IO) { ClipStore.write(runtime.clipsDir, id, pcm) }
                if (!ClipCommit.allow(session, holdSession) || holdMuted) return@launch
                val message = DirectMessage(
                    id = id,
                    fromDeviceId = deviceId(),
                    toDeviceId = peer,
                    senderName = displayName(),
                    body = body.take(800),
                    createdAtMillis = System.currentTimeMillis(),
                    kind = Ptt.CLIP,
                    audioPath = audioPath,
                    rawBody = raw.take(800).ifEmpty { null },
                )
                withContext(Dispatchers.IO) {
                    if (!ClipCommit.allow(session, holdSession) || holdMuted) {
                        audioPath?.let { File(it).delete() }
                        return@withContext
                    }
                    if (runtime.directStore.addIfCurrent(message, epoch) == null) {
                        audioPath?.let { File(it).delete() }
                        return@withContext
                    }
                    runtime.relay.broadcastDirect(message)
                    runtime.refreshDirect()
                }
                if (Ptt.either(body, raw)) publishCall(emergency = body)
            }
            publishCall()
        }
    }

    /** Stops the mic and throws away the clip. Mute must not transcribe or send. */
    private fun discardHold() {
        holdSession += 1
        val wasLive = holding || recorder.isRunning
        holding = false
        holdElapsed = 0
        transcribingHold = false
        if (wasLive) publishCall()
        if (!recorder.isRunning) return
        viewModelScope.launch {
            recordGate.withLock {
                if (!recorder.isRunning) return@withLock
                runtime.relay.onLocalRecordingFinished()
                recorder.stop()
            }
        }
    }

    private fun watchRing() {
        val token = ring.armToken()
        viewModelScope.launch {
            delay(Hangup.RING_MS)
            syncClock()
            if (!ring.due(token)) return@launch
            if (ring.call.phase != CallPhase.OUTGOING && ring.call.phase != CallPhase.INCOMING) return@launch
            endCall()
        }
    }

    private fun syncClock() {
        ring.now = System.currentTimeMillis()
    }

    private fun noteCall(next: CallState) {
        callSerial = CallSession.nextId(
            wasIdle = call.phase == CallPhase.IDLE,
            nowIdle = next.phase == CallPhase.IDLE,
            currentId = callSerial,
        )
        call = next
        ring.adopt(call)
    }

    /** A muted hold is dropped. Mute itself clears only after the call is idle. */
    private fun finishHoldOnEnd(holdingNow: Boolean) {
        if (HoldMute.dropInFlight(holdMuted) && (holdingNow || holding || recorder.isRunning)) {
            discardHold()
        } else if (holdingNow || holding) {
            stopHold()
        }
        holdMuted = HoldMute.afterEnd()
    }

    private suspend fun hear(engine: WhisperTranscriber, pcm: FloatArray): SpeechHearing {
        val names = contactNames()
        val raw = withContext(Dispatchers.Default) { engine.transcribe(pcm, _sos.value.language.whisperCode, names) }
        val heard = SpeechHearing.interpret(raw, names)
        if (heard.raw == TranscriptLimit.UNAVAILABLE) return heard
        if (!CaptionCleanup.allowModel(heard.urgency, heard.raw, heard.shown)) return heard
        val cleaned = withContext(Dispatchers.IO) { GemmaSummarizer.cleanupCaption(heard.shown) } ?: return heard
        if (!CaptionCleanup.acceptModel(heard.shown, cleaned)) return heard
        val shown = SentenceCase.apply(cleaned)
        val urgency = SpeechHearing.higher(TriageEngine.triage(heard.raw), TriageEngine.triage(shown)).urgency
        return heard.copy(shown = shown, urgency = urgency)
    }

    private fun contactNames(): List<String> =
        runtime.directStore.rows().filter { it.saved }.map { it.name }

    private fun applyRing() {
        val next = ring.call
        val same = next.phase == call.phase && next.peerId == call.peerId
        noteCall(if (same) next.copy(playing = call.playing, speakerOn = call.speakerOn) else next)
    }

    private fun flushRingSends() {
        for (out in ring.drain()) {
            val body = when (out.kind) {
                Ptt.END -> "End"
                Ptt.ACCEPT -> "Accept"
                else -> "Decline"
            }
            sendDirect(out.peerId, body, kind = out.kind)
        }
    }

    private val dismissedEmergency = HashSet<String>()
    private var holding = false
    private var holdMuted = false
    private var transcribingHold = false
    private var holdElapsed = 0
    @Volatile private var holdSession = 0
    private var callSerial = 0L
    private var holdStartedAt = 0L
    private var savedMusicVolume = -1

    private fun absorbSignals(messages: List<DirectMessage>) {
        val myId = deviceId()
        val wasLive = call.phase != CallPhase.IDLE
        syncClock()
        val startRing = ring.absorb(messages, myId)
        applyRing()
        if (wasLive && call.phase == CallPhase.IDLE) finishHoldOnEnd(holding || _call.value.holding)
        if (startRing) watchRing()
        for (message in messages.sortedBy { it.createdAtMillis }) {
            if (message.kind != Ptt.CLIP || message.toDeviceId != myId) continue
            val path = ClipPlay.pending(message.id, message.audioPath, playedClips)
            if (
                path != null &&
                call.phase == CallPhase.ACTIVE &&
                message.fromDeviceId == call.peerId
            ) {
                val started = playCallClip(path)
                val nextPlayed = ClipPlay.remember(message.id, started, playedClips.toSet())
                playedClips.clear()
                playedClips.addAll(nextPlayed)
                if (started) noteCall(call.copy(playing = true))
            }
        }
        val emergency = if (call.phase == CallPhase.ACTIVE) {
            runtime.directStore.thread(myId, call.peerId)
                .filter { it.kind == Ptt.CLIP && it.id !in dismissedEmergency }
                .lastOrNull { Ptt.either(it.body, it.rawBody) }
                ?.body
        } else {
            null
        }
        publishCall(emergency)
    }

    private fun publishCall(emergency: String? = _call.value.emergency) {
        val peer = call.peerId
        val captions = if (peer.isBlank()) {
            emptyList()
        } else {
            val lines = runtime.directStore.thread(deviceId(), peer)
                .filter { it.kind == Ptt.CLIP }
                .takeLast(if (transcribingHold) 2 else 3)
                .map { message ->
                    val mine = isMine(message)
                    CaptionLine(
                        id = message.id,
                        mine = mine,
                        speaker = if (mine) "You" else message.senderName,
                        text = CaptionDisplay.text(message.body),
                        transcribing = false,
                        raw = message.rawBody,
                    )
                }
            if (transcribingHold) {
                lines + CaptionLine(id = "live", mine = true, speaker = "You", text = "Transcribing…", transcribing = true)
            } else {
                lines
            }
        }
        _call.value = CallUi(
            phase = call.phase,
            peerId = peer,
            peerName = call.peerName.ifBlank { peer },
            speakerOn = call.speakerOn,
            holding = holding,
            elapsedSec = holdElapsed,
            playing = call.playing,
            captions = captions,
            emergency = emergency,
            muted = holdMuted,
            callId = callSerial,
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun routeCallAudio(speaker: Boolean) {
        val audio = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.mode = AudioManager.MODE_NORMAL
        audio.isSpeakerphoneOn = true
        raiseMusic(audio)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .build()
        callFocus = request
        audio.requestAudioFocus(request)
        noteCall(call.copy(speakerOn = true))
    }

    private fun raiseMusic(audio: AudioManager) {
        if (savedMusicVolume < 0) {
            savedMusicVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * 0.85f).toInt().coerceIn(1, max)
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) < target) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
        }
    }

    private fun restoreMusic(audio: AudioManager) {
        if (savedMusicVolume < 0) return
        val restore = savedMusicVolume
        savedMusicVolume = -1
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, restore, 0) }
    }

    private fun releaseCallAudio() {
        val audio = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        callFocus?.let { audio.abandonAudioFocusRequest(it) }
        callFocus = null
        restoreMusic(audio)
        audio.isSpeakerphoneOn = false
        audio.mode = AudioManager.MODE_NORMAL
        noteCall(call.copy(playing = false, speakerOn = false))
    }

    private fun playCallClip(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) {
            noteCall(call.copy(playing = false))
            return false
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val next = MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(file.absolutePath)
                    setOnCompletionListener { done ->
                        viewModelScope.launch(Dispatchers.Main) {
                            done.release()
                            if (player === done) player = null
                            noteCall(call.copy(playing = false))
                            publishCall()
                        }
                    }
                    prepare()
                }
                withContext(Dispatchers.Main) {
                    player?.release()
                    player = next
                    next.start()
                    noteCall(call.copy(playing = true))
                    publishCall()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    noteCall(call.copy(playing = false))
                    publishCall()
                }
            }
        }
        return true
    }

    override fun onCleared() {
        if (recorder.isRunning) recorder.stop()
        player?.release()
        transcriber?.release() // frees the native context on the whisper thread
        super.onCleared()
    }
}
