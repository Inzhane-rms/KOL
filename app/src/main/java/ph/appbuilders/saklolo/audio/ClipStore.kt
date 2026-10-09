package ph.appbuilders.saklolo.audio

import java.io.File

/** Writes a loudness-matched clip. AAC-LC m4a when the platform encoder works, otherwise WAV. */
object ClipStore {
    fun write(dir: File, id: String, samples: FloatArray): String? {
        if (samples.isEmpty()) return null
        val shaped = Loudness.apply(samples)
        val encoded = AacClip.encode(dir, id, shaped)
        if (encoded != null) return encoded.absolutePath
        val wav = WavPcm.clipFile(dir, id)
        return try {
            WavPcm.write(wav, shaped)
            if (wav.exists() && wav.length() > WavPcm.HEADER_BYTES) wav.absolutePath else null
        } catch (_: Exception) {
            null
        }
    }
}
