package ph.appbuilders.saklolo.stt

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

class PcmRecorder {
    private val lock = Any()
    private val samples = ArrayList<Float>(SAMPLE_RATE * 20)
    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null

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
        audioRecord?.release()
        audioRecord = null
        return synchronized(lock) { samples.toFloatArray() }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SECONDS = 30
    }
}
