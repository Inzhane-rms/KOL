package ph.appbuilders.saklolo.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Audio that reaches Whisper is 16 kHz mono, high-passed, peak/RMS normalized,
 * and trimmed of leading and trailing silence.
 */
object SpeechPrep {
    const val SAMPLE_RATE = 16_000
    const val HIGH_PASS_HZ = 80.0

    fun prepare(input: FloatArray, sampleRate: Int = SAMPLE_RATE): FloatArray {
        if (input.isEmpty()) return input
        return trimSilence(normalize(highPass(input, sampleRate)), sampleRate)
    }

    fun highPass(input: FloatArray, sampleRate: Int = SAMPLE_RATE, cutoffHz: Double = HIGH_PASS_HZ): FloatArray {
        if (input.isEmpty()) return input
        val alpha = exp(-2.0 * PI * cutoffHz / sampleRate).toFloat()
        val out = FloatArray(input.size)
        var prevX = 0f
        var prevY = 0f
        for (index in input.indices) {
            val sample = input[index]
            val filtered = alpha * (prevY + sample - prevX)
            out[index] = filtered
            prevX = sample
            prevY = filtered
        }
        return out
    }

    /** Lift quiet speech toward a steady RMS, then stop the peak from clipping. */
    fun normalize(input: FloatArray): FloatArray {
        if (input.isEmpty()) return input
        var sum = 0.0
        var peak = 0f
        for (sample in input) {
            val magnitude = abs(sample)
            if (magnitude > peak) peak = magnitude
            sum += sample.toDouble() * sample
        }
        val rms = sqrt(sum / input.size).toFloat()
        if (peak < 1e-5f || rms < 1e-6f) return input
        var gain = 0.1f / rms
        if (peak * gain > 0.95f) gain = 0.95f / peak
        return FloatArray(input.size) { index -> (input[index] * gain).coerceIn(-1f, 1f) }
    }

    /** Drop leading and trailing frames whose energy is far below the loudest frame. */
    fun trimSilence(input: FloatArray, sampleRate: Int = SAMPLE_RATE): FloatArray {
        if (input.isEmpty()) return input
        val frame = sampleRate / 50
        if (frame <= 0 || input.size < frame) return input
        val frames = input.size / frame
        val energy = FloatArray(frames)
        var maxEnergy = 0f
        for (index in 0 until frames) {
            var sum = 0.0
            val start = index * frame
            for (offset in 0 until frame) {
                val sample = input[start + offset].toDouble()
                sum += sample * sample
            }
            val value = (sum / frame).toFloat()
            energy[index] = value
            if (value > maxEnergy) maxEnergy = value
        }
        if (maxEnergy <= 1e-8f) return FloatArray(0)
        val threshold = maxEnergy * 0.02f
        var first = 0
        while (first < frames && energy[first] < threshold) first++
        var last = frames - 1
        while (last > first && energy[last] < threshold) last--
        val startFrame = (first - 1).coerceAtLeast(0)
        val endFrame = (last + 2).coerceAtMost(frames)
        return input.copyOfRange(startFrame * frame, endFrame * frame)
    }
}
