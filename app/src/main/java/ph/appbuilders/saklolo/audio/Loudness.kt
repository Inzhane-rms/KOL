package ph.appbuilders.saklolo.audio

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Sender-side loudness. -16 LUFS is approximated as -16 dB RMS, then a peak limiter.
 */
object Loudness {
    const val TARGET_DB = -16.0
    const val PEAK_LIMIT = 0.98

    fun targetLinear(): Double = 10.0.pow(TARGET_DB / 20.0)

    fun gain(rms: Double, peak: Double): Double {
        if (rms <= 1e-8 || peak <= 1e-8) return 1.0
        var applied = targetLinear() / rms
        if (peak * applied > PEAK_LIMIT) applied = PEAK_LIMIT / peak
        return applied
    }

    fun apply(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        var sum = 0.0
        var peak = 0.0
        for (sample in samples) {
            val magnitude = abs(sample).toDouble()
            if (magnitude > peak) peak = magnitude
            sum += sample.toDouble() * sample
        }
        val rms = sqrt(sum / samples.size)
        val applied = gain(rms, peak).toFloat()
        return FloatArray(samples.size) { index -> (samples[index] * applied).coerceIn(-1f, 1f) }
    }
}
