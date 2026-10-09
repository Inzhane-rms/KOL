package ph.appbuilders.saklolo.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
}
