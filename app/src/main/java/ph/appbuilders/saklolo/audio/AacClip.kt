package ph.appbuilders.saklolo.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AAC-LC mono at about 24 kbps in an m4a container.
 * Uses the platform encoder. No extra native library.
 */
object AacClip {
    const val BIT_RATE = 24_000
    const val SAMPLE_RATE = 16_000

    fun file(dir: File, id: String): File {
        val safe = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "$safe.m4a")
    }

    fun encode(dir: File, id: String, samples: FloatArray): File? {
        if (samples.isEmpty()) return null
        val dest = file(dir, id)
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var started = false
        return try {
            dest.parentFile?.mkdirs()
            if (dest.exists()) dest.delete()
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
                setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(dest.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val info = MediaCodec.BufferInfo()
            var offset = 0
            var inputDone = false
            var outputDone = false
            var track = -1
            var spins = 0
            val limit = samples.size / 160 + 4_000
            while (!outputDone && spins < limit) {
                spins++
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val input = codec.getInputBuffer(inIndex) ?: continue
                        input.clear()
                        val room = input.remaining() / 2
                        val count = minOf(room, samples.size - offset)
                        if (count <= 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val bytes = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
                            for (index in 0 until count) {
                                val clamped = samples[offset + index].coerceIn(-1f, 1f)
                                bytes.putShort((clamped * 32767f).toInt().toShort())
                            }
                            input.put(bytes.array())
                            val timeUs = offset * 1_000_000L / SAMPLE_RATE
                            codec.queueInputBuffer(inIndex, 0, count * 2, timeUs, 0)
                            offset += count
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                    }
                    outIndex >= 0 -> {
                        val output = codec.getOutputBuffer(outIndex)
                        if (output != null && info.size > 0 && started &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            muxer.writeSampleData(track, output, info)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            if (!started || !dest.exists() || dest.length() < 32) {
                dest.delete()
                null
            } else {
                dest
            }
        } catch (_: Throwable) {
            dest.delete()
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }
}
