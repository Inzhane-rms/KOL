package ph.appbuilders.saklolo.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs

/** Peak bars for a voice clip. WAV is read directly. AAC is decoded to PCM, off the caller’s thread, then cached. */
object Waveform {
    const val WAV = "wav"
    const val AAC = "aac"

    private val cache = object : LinkedHashMap<String, List<Float>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Float>>?): Boolean = size > 32
    }

    fun decoder(file: File): String = if (WavPcm.isWav(file)) WAV else AAC

    fun barsFromPcm(samples: ShortArray, bars: Int): List<Float> {
        if (bars <= 0 || samples.isEmpty()) return emptyList()
        val peaks = FloatArray(bars)
        for (index in samples.indices) {
            val bucket = (index.toLong() * bars / samples.size).toInt().coerceIn(0, bars - 1)
            val amp = abs(samples[index].toInt()) / 32767f
            if (amp > peaks[bucket]) peaks[bucket] = amp
        }
        val max = peaks.maxOrNull()?.takeIf { it > 0f } ?: return List(bars) { 0.08f }
        return peaks.map { (it / max).coerceIn(0.08f, 1f) }
    }

    fun bars(file: File, count: Int = 24): List<Float> {
        if (count <= 0 || !file.exists()) return emptyList()
        val key = "${file.absolutePath}:${file.length()}:${file.lastModified()}:$count"
        synchronized(cache) { cache[key]?.let { return it } }
        val peaks = when (decoder(file)) {
            WAV -> WavPcm.peakBars(file, count)
            else -> {
                val pcm = AacPcm.decode(file) ?: return emptyList()
                barsFromPcm(pcm, count)
            }
        }
        synchronized(cache) { cache[key] = peaks }
        return peaks
    }
}

/** Platform AAC decode. Call from a background dispatcher. Unit tests never reach this. */
internal object AacPcm {
    fun decode(file: File): ShortArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(file.absolutePath)
            var track = -1
            var format: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    track = index
                    format = candidate
                    break
                }
            }
            if (track < 0 || format == null) return null
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            val samples = ArrayList<Short>()
            var inputDone = false
            var outputDone = false
            var spins = 0
            while (!outputDone && spins < 8_000) {
                spins++
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val input = codec.getInputBuffer(inIndex) ?: continue
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    val output = codec.getOutputBuffer(outIndex)
                    if (output != null && info.size > 0) {
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        output.order(ByteOrder.LITTLE_ENDIAN)
                        while (output.remaining() >= 2) samples.add(output.short)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            if (samples.isEmpty()) null else samples.toShortArray()
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
