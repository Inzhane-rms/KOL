package ph.appbuilders.saklolo.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** 16 kHz mono 16-bit PCM WAV. Caps the clip at 30 seconds. */
object WavPcm {
    const val SAMPLE_RATE = 16_000
    const val MAX_SECONDS = 30
    const val HEADER_BYTES = 44

    fun write(file: File, samples: FloatArray, sampleRate: Int = SAMPLE_RATE) {
        val count = samples.size.coerceAtMost(sampleRate * MAX_SECONDS)
        val dataBytes = count * 2
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            header.put(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
            header.putInt(36 + dataBytes)
            header.put(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))
            header.put(byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte()))
            header.putInt(16)
            header.putShort(1)
            header.putShort(1)
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2)
            header.putShort(2)
            header.putShort(16)
            header.put(byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
            header.putInt(dataBytes)
            out.write(header.array())
            val data = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until count) {
                val clamped = samples[i].coerceIn(-1f, 1f)
                data.putShort((clamped * 32767f).toInt().toShort())
            }
            out.write(data.array())
        }
    }

    fun clipFile(dir: File, alertId: String): File {
        val safe = alertId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "$safe.wav")
    }

    /** Keep a received WAV as wav. Anything else (including AAC m4a) keeps an m4a name so playback still works. */
    fun storedClip(dir: File, id: String, source: File): File {
        val safe = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val ext = if (isWav(source)) "wav" else "m4a"
        return File(dir, "$safe.$ext")
    }

    fun isWav(file: File): Boolean {
        if (!file.exists() || file.length() < 12) return false
        val header = ByteArray(12)
        file.inputStream().use { stream ->
            if (stream.read(header) < 12) return false
        }
        val riff = header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte()
        val wave = header[8] == 'W'.code.toByte() &&
            header[9] == 'A'.code.toByte() &&
            header[10] == 'V'.code.toByte() &&
            header[11] == 'E'.code.toByte()
        return riff && wave
    }

    /** Peak amplitude in each bucket, scaled to 0.08..1. Empty when the file is not a clip. */
    fun peakBars(file: File, bars: Int = 28): List<Float> {
        if (bars <= 0 || !file.exists() || file.length() <= HEADER_BYTES) return emptyList()
        val data = file.readBytes()
        val samples = (data.size - HEADER_BYTES) / 2
        if (samples <= 0) return emptyList()
        val peaks = FloatArray(bars)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samples) {
            val sample = buffer.getShort(HEADER_BYTES + i * 2).toInt()
            val bucket = (i.toLong() * bars / samples).toInt().coerceIn(0, bars - 1)
            val amp = abs(sample) / 32767f
            if (amp > peaks[bucket]) peaks[bucket] = amp
        }
        val max = peaks.maxOrNull()?.takeIf { it > 0f } ?: return List(bars) { 0.08f }
        return peaks.map { (it / max).coerceIn(0.08f, 1f) }
    }

    fun durationLabel(file: File): String {
        if (!file.exists() || file.length() <= HEADER_BYTES) return "0:00"
        val samples = (file.length() - HEADER_BYTES) / 2
        val sec = (samples / SAMPLE_RATE).toInt()
        return "%d:%02d".format(sec / 60, sec % 60)
    }
}
