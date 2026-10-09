package ph.appbuilders.saklolo

import android.app.Application
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
import kotlinx.coroutines.withContext
import ph.appbuilders.saklolo.ask.AskEngine
import ph.appbuilders.saklolo.ask.AskResult
import ph.appbuilders.saklolo.ask.AskTurn
import ph.appbuilders.saklolo.audio.WavPcm
import ph.appbuilders.saklolo.group.ConcertGroup
import ph.appbuilders.saklolo.group.GroupNote
import ph.appbuilders.saklolo.group.GroupQr
import ph.appbuilders.saklolo.group.GroupTriage
import ph.appbuilders.saklolo.group.Sighting
import ph.appbuilders.saklolo.location.DeviceLocation
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.relay.NearbyPeer
import ph.appbuilders.saklolo.relay.QrCodec
import ph.appbuilders.saklolo.stt.ModelInstaller
import ph.appbuilders.saklolo.stt.PcmRecorder
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
    val status: String = "",
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
    private var recordGeneration = 0
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

    private val _voice = MutableStateFlow(VoiceUiState())
    val voice: StateFlow<VoiceUiState> = _voice.asStateFlow()

    private val _askTurns = MutableStateFlow<List<AskTurn>>(emptyList())
    val askTurns: StateFlow<List<AskTurn>> = _askTurns.asStateFlow()
    private var nextAskId = 1L
    private var askBank: SafetyBank? = null

    private val _sos = MutableStateFlow(SosUiState(language = runtime.settings.language))
    val sos: StateFlow<SosUiState> = _sos.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
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
                transcriber = WhisperTranscriber(model)
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

    fun onTranscriptChange(text: String) {
        draftGeneration++
        val triage = TriageEngine.triage(text)
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
        viewModelScope.launch {
            if (!recordGate.tryLock()) return@launch
            val pcm = try {
                if (!_sos.value.recording && !recorder.isRunning) return@launch
                runtime.relay.onLocalRecordingFinished()
                recorder.stop()
            } finally {
                recordGate.unlock()
            }
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
            if (!recordGate.tryLock()) return@launch
            try {
                _sos.update { it.copy(canUndo = false, recording = false, status = "Transcribing on this phone…", error = null) }
                finishRecording(pcm)
            } catch (error: Exception) {
                _sos.update {
                    it.copy(status = "Transcription failed", error = error.message ?: "Unknown error")
                }
            } finally {
                recordGate.unlock()
            }
        }
    }

    fun stopRecording() {
        heldPcm = null
        undoGeneration += 1
        viewModelScope.launch {
            if (!recordGate.tryLock()) return@launch
            try {
                if (!_sos.value.recording && !recorder.isRunning) return@launch
                runtime.relay.onLocalRecordingFinished()
                _sos.update {
                    it.copy(recording = false, canUndo = false, micLevel = 0f, status = "Transcribing on this phone…", error = null)
                }
                finishRecording(recorder.stop())
            } catch (error: Exception) {
                _sos.update {
                    it.copy(status = "Transcription failed", error = error.message ?: "Unknown error")
                }
            } finally {
                recordGate.unlock()
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
        val language = _sos.value.language
        val text = withContext(Dispatchers.Default) {
            engine.transcribe(pcm, language.whisperCode)
        }
        if (text.isBlank()) {
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
        onTranscriptChange(text)
        refineWithGemma(text, draftGeneration)
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
        val audioPath = lastPcm?.let { samples ->
            val file = WavPcm.clipFile(runtime.clipsDir, id)
            WavPcm.write(file, samples)
            if (file.exists() && file.length() > WavPcm.HEADER_BYTES) file.absolutePath else null
        }
        lastPcm = null
        val alert = Alert(
            id = id,
            transcript = state.transcript.trim().take(800),
            summary = state.summary.take(180),
            urgency = state.urgency,
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

    fun setDisplayName(name: String) {
        runtime.settings.displayName = name
    }

    fun createGroup(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val id = GroupQr.newId()
        runtime.groupStore.join(id, trimmed, System.currentTimeMillis())
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
        )
        runtime.groupStore.addLocal(note)
        runtime.relay.broadcastNote(note)
        runtime.refreshGroups()
    }

    fun startVoiceNote() {
        if (runtime.groupStore.groups().isEmpty()) return
        if (_sos.value.recording || _voice.value.recording) return
        if (!_sos.value.modelReady) {
            _voice.update { it.copy(status = "The speech model is not ready yet.") }
            return
        }
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
        _voice.update { it.copy(recording = true, elapsedSec = 0, status = "Listening…") }
        viewModelScope.launch {
            while (recorder.isRunning && _voice.value.recording && generation == recordGeneration) {
                delay(250)
                if (generation != recordGeneration) return@launch
                val elapsed = ((System.currentTimeMillis() - recordingStartedAt) / 1000).toInt()
                _voice.update { it.copy(elapsedSec = elapsed) }
                if (generation == recordGeneration && (elapsed >= PcmRecorder.MAX_SECONDS || !recorder.isRunning)) {
                    stopVoiceNote()
                    break
                }
            }
        }
    }

    fun stopVoiceNote() {
        viewModelScope.launch {
            if (!recordGate.tryLock()) return@launch
            try {
                if (!_voice.value.recording && !recorder.isRunning) return@launch
                runtime.relay.onLocalRecordingFinished()
                _voice.update { it.copy(recording = false, status = "Transcribing on this phone…") }
                finishVoice(recorder.stop())
            } catch (error: Exception) {
                _voice.update { it.copy(recording = false, status = error.message ?: "Transcription failed") }
            } finally {
                recordGate.unlock()
            }
        }
    }

    private suspend fun finishVoice(pcm: FloatArray) {
        if (pcm.size < PcmRecorder.SAMPLE_RATE / 2) {
            _voice.update { it.copy(status = "Recording was too short") }
            return
        }
        val engine = transcriber ?: run {
            _voice.update { it.copy(status = "The speech model is not ready yet.") }
            return
        }
        val text = withContext(Dispatchers.Default) {
            engine.transcribe(pcm, _sos.value.language.whisperCode)
        }
        if (text.isBlank()) {
            _voice.update { it.copy(status = "No speech recognized") }
            return
        }
        val group = runtime.groupStore.active() ?: return
        val location = DeviceLocation.lastKnown(getApplication())
        val id = java.util.UUID.randomUUID().toString()
        val audioPath = run {
            val file = WavPcm.clipFile(runtime.clipsDir, id)
            WavPcm.write(file, pcm)
            if (file.exists() && file.length() > WavPcm.HEADER_BYTES) file.absolutePath else null
        }
        val note = GroupNote(
            id = id,
            groupId = group.id,
            sender = runtime.settings.displayName,
            body = text.trim().take(800),
            createdAtMillis = System.currentTimeMillis(),
            audioPath = audioPath,
            lat = location?.first,
            lon = location?.second,
            urgency = GroupTriage.labelVoice(text),
        )
        runtime.groupStore.addLocal(note)
        runtime.relay.broadcastNote(note)
        runtime.refreshGroups()
        _voice.update { it.copy(status = "Voice note sent") }
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
        val group = GroupQr.decode(payload)
        if (group != null) {
            runtime.groupStore.join(group.id, group.name, System.currentTimeMillis())
            runtime.refreshGroups()
            _notice.value = "Joined ${group.name}"
            return
        }
        val alert = QrCodec.decode(payload)
        if (alert == null) {
            _notice.value = "That QR is not a B-LINK code"
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
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    it.release()
                    if (player === it) player = null
                }
                prepare()
                start()
            }
        } catch (error: Exception) {
            _sos.update { it.copy(error = error.message ?: "Could not play the voice clip") }
        }
    }

    override fun onCleared() {
        if (recorder.isRunning) recorder.stop()
        player?.release()
        transcriber?.release() // frees the native context on the whisper thread
        super.onCleared()
    }
}
