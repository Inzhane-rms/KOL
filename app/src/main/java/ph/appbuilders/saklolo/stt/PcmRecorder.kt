package ph.appbuilders.saklolo.stt

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor

class PcmRecorder {
    private val lock = Any()
    private val samples = ArrayList<Float>(SAMPLE_RATE * 20)
    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null
    private var gainControl: AutomaticGainControl? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start(): String? {
        if (isRunning) return null
        val min = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) return "This phone cannot record 16 kHz audio."
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            min * 2,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return "Microphone is not available."
        }
        synchronized(lock) { samples.clear() }
        isRunning = true
        audioRecord = recorder
        enableEffects(recorder.audioSessionId)
        recorder.startRecording()
        thread = Thread({
            val buffer = ShortArray(min)
            while (isRunning) {
                val count = recorder.read(buffer, 0, buffer.size)
                if (count <= 0) continue
                synchronized(lock) {
                    val room = SAMPLE_RATE * MAX_SECONDS - samples.size
                    val take = minOf(count, room.coerceAtLeast(0))
                    for (index in 0 until take) {
                        samples.add(buffer[index] / 32768f)
                    }
                    if (samples.size >= SAMPLE_RATE * MAX_SECONDS) {
                        isRunning = false
                    }
                }
            }
        }, "saklolo-mic")
        thread?.start()
        return null
    }

    fun recentPeak(): Float = synchronized(lock) {
        if (samples.isEmpty()) return 0f
        val start = (samples.size - 800).coerceAtLeast(0)
        var peak = 0f
        for (index in start until samples.size) {
            val sample = kotlin.math.abs(samples[index])
            if (sample > peak) peak = sample
        }
        peak
    }

    fun stop(): FloatArray {
        isRunning = false
        try {
            thread?.join(1500)
        } catch (_: InterruptedException) {
        }
        thread = null
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
        }
        releaseEffects()
        audioRecord?.release()
        audioRecord = null
        return synchronized(lock) { samples.toFloatArray() }
    }

    private fun enableEffects(sessionId: Int) {
        if (AutomaticGainControl.isAvailable()) {
            gainControl = runCatching { AutomaticGainControl.create(sessionId) }.getOrNull()
            gainControl?.enabled = true
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = runCatching { NoiseSuppressor.create(sessionId) }.getOrNull()
            noiseSuppressor?.enabled = true
        }
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = runCatching { AcousticEchoCanceler.create(sessionId) }.getOrNull()
            echoCanceler?.enabled = true
        }
    }

    private fun releaseEffects() {
        listOf(gainControl, noiseSuppressor, echoCanceler).forEach { effect ->
            runCatching { effect?.release() }
        }
        gainControl = null
        noiseSuppressor = null
        echoCanceler = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SECONDS = 30
    }
}
