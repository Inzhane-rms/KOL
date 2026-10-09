package ph.appbuilders.saklolo

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import ph.appbuilders.saklolo.location.DeviceLocation
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.model.AlertStore
import ph.appbuilders.saklolo.relay.NearbyRelay
import ph.appbuilders.saklolo.relay.QrCodec
import ph.appbuilders.saklolo.stt.ModelInstaller
import ph.appbuilders.saklolo.stt.PcmRecorder
import ph.appbuilders.saklolo.stt.SpeechLanguage
import ph.appbuilders.saklolo.stt.WhisperTranscriber
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
    val actionable: Boolean = false,
    val error: String? = null,
    val sentSummary: String? = null,
)

data class RelayUiState(
    val enabled: Boolean = false,
    val peers: Int = 0,
    val message: String = "Relay is off",
    val notice: String? = null,
)

class SakloloViewModel(app: Application) : AndroidViewModel(app) {
    private val store = AlertStore(File(app.filesDir, "alerts.json"))
    private val recorder = PcmRecorder()
    private val recordGate = Mutex()
    private var transcriber: WhisperTranscriber? = null
    private var recordingStartedAt = 0L

    private val relay = NearbyRelay(
        context = app,
        store = store,
        onAlertsChanged = { refreshAlerts() },
        onStatus = { peers, message ->
            _relay.update { it.copy(peers = peers, message = message) }
        },
    )

    private val _alerts = MutableStateFlow(store.snapshot())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private val _sos = MutableStateFlow(SosUiState())
    val sos: StateFlow<SosUiState> = _sos.asStateFlow()

    private val _relay = MutableStateFlow(RelayUiState())
    val relayState: StateFlow<RelayUiState> = _relay.asStateFlow()

    private val localName: String

    init {
        val androidId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID) ?: "phone"
        localName = "Saklolo-" + androidId.takeLast(4)
        viewModelScope.launch {
            try {
                val model = ModelInstaller.ensure(app) { progress ->
                    _sos.update { it.copy(modelStatus = progress) }
                }
                transcriber = WhisperTranscriber(model)
                _sos.update {
                    it.copy(modelReady = true, modelStatus = "Speech model ready on this phone")
                }
            } catch (error: Exception) {
                _sos.update {
                    it.copy(
                        modelReady = false,
                        modelStatus = "Speech model unavailable",
                        error = error.message,
                    )
                }
            }
        }
    }

    fun setLanguage(language: SpeechLanguage) {
        _sos.update { it.copy(language = language) }
    }

    fun onTranscriptChange(text: String) {
        val triage = TriageEngine.triage(text)
        _sos.update {
            it.copy(
                transcript = text,
                summary = if (triage.actionable) triage.summary else "",
                urgency = if (triage.actionable) triage.urgency else null,
                actionable = triage.actionable,
                sentSummary = null,
                error = null,
                status = if (triage.actionable) "Review, then send" else "Type or record an SOS",
            )
        }
    }

    fun startRecording() {
        if (_sos.value.recording) return
        if (!_sos.value.modelReady) {
            _sos.update { it.copy(error = "The speech model is not ready yet.") }
            return
        }
        val failure = recorder.start()
        if (failure != null) {
            _sos.update { it.copy(error = failure, status = "Could not record") }
            return
        }
        recordingStartedAt = System.currentTimeMillis()
        _sos.update {
            it.copy(
                recording = true,
                elapsedSec = 0,
                error = null,
                sentSummary = null,
                status = "Listening… speak the SOS",
            )
        }
        viewModelScope.launch {
            while (recorder.isRunning && _sos.value.recording) {
                delay(250)
                val elapsed = ((System.currentTimeMillis() - recordingStartedAt) / 1000).toInt()
                _sos.update { state -> state.copy(elapsedSec = elapsed) }
                if (elapsed >= PcmRecorder.MAX_SECONDS || !recorder.isRunning) {
                    stopRecording()
                    break
                }
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            if (!recordGate.tryLock()) return@launch
            try {
                if (!_sos.value.recording && !recorder.isRunning) return@launch
                _sos.update { it.copy(recording = false, status = "Transcribing on this phone…", error = null) }
                val pcm = recorder.stop()
                if (pcm.size < PcmRecorder.SAMPLE_RATE / 2) {
                    _sos.update {
                        it.copy(status = "Recording was too short", error = "Hold the button a little longer and speak clearly.")
                    }
                    return@launch
                }
                val peak = pcm.maxOf { abs(it) }
                if (peak < 0.01f) {
                    _sos.update {
                        it.copy(status = "Too quiet", error = "The mic barely heard anything. Move closer and try again.")
                    }
                    return@launch
                }
                val engine = transcriber ?: error("Speech model is not loaded")
                val text = engine.transcribe(pcm, _sos.value.language.whisperCode)
                if (text.isBlank()) {
                    _sos.update {
                        it.copy(
                            status = "No speech recognized",
                            error = "Try again, or type the SOS below. Bisaya is the weakest of the three.",
                        )
                    }
                    return@launch
                }
                onTranscriptChange(text)
            } catch (error: Exception) {
                _sos.update {
                    it.copy(status = "Transcription failed", error = error.message ?: "Unknown error")
                }
            } finally {
                recordGate.unlock()
            }
        }
    }

    fun sendDraft() {
        val state = _sos.value
        if (!state.actionable || state.urgency == null) return
        val location = DeviceLocation.lastKnown(getApplication())
        val alert = Alert(
            id = java.util.UUID.randomUUID().toString(),
            transcript = state.transcript.trim().take(800),
            summary = state.summary.take(180),
            urgency = state.urgency,
            createdAtMillis = System.currentTimeMillis(),
            lat = location?.first,
            lon = location?.second,
            hops = 0,
            language = state.language.name,
        )
        store.addLocal(alert)
        refreshAlerts()
        relay.broadcast(alert)
        _sos.update {
            it.copy(
                transcript = "",
                summary = "",
                urgency = null,
                actionable = false,
                status = "Alert is on this phone and queued for relay",
                sentSummary = alert.summary,
                error = null,
            )
        }
    }

    fun discardDraft() {
        _sos.update {
            it.copy(
                transcript = "",
                summary = "",
                urgency = null,
                actionable = false,
                status = "Hold the button and speak. Tagalog, Bisaya, or English.",
                error = null,
            )
        }
    }

    fun setRelayEnabled(enabled: Boolean) {
        if (enabled) {
            relay.start(localName)
            _relay.update { it.copy(enabled = true, message = "Looking for nearby Saklolo phones") }
        } else {
            relay.stop()
            _relay.update { it.copy(enabled = false, peers = 0, message = "Relay off") }
        }
    }

    fun onLocationPermissionGranted() {
        DeviceLocation.start(getApplication())
    }

    fun ingestQr(payload: String) {
        val alert = QrCodec.decode(payload)
        if (alert == null) {
            _relay.update { it.copy(notice = "That QR is not a Saklolo alert") }
            return
        }
        val fresh = store.ingest(listOf(alert))
        refreshAlerts()
        if (fresh.isEmpty()) {
            _relay.update { it.copy(notice = "This phone already has that alert") }
        } else {
            relay.broadcast(alert)
            _relay.update { it.copy(notice = "Added from QR: ${alert.summary}") }
        }
    }

    fun clearNotice() {
        _relay.update { it.copy(notice = null) }
    }

    fun removeAlert(id: String) {
        store.remove(id)
        refreshAlerts()
    }

    fun qrText(alert: Alert): String = QrCodec.encode(alert)

    private fun refreshAlerts() {
        _alerts.value = store.snapshot()
    }

    override fun onCleared() {
        if (recorder.isRunning) recorder.stop()
        relay.stop()
        transcriber?.release()
        super.onCleared()
    }
}
