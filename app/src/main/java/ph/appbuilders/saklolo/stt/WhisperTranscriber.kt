package ph.appbuilders.saklolo.stt

import android.content.SharedPreferences
import android.util.Log
import java.io.File
import ph.appbuilders.saklolo.audio.SpeechPrep
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Each decode gets its own id. An abort covers only ids already opened.
 * A later decode is a new id, so a leftover abort cannot mark it unavailable.
 */
internal class DecodeGeneration {
    private val next = AtomicInteger(1)
    private val abortedThrough = AtomicInteger(0)

    fun open(): Int = next.getAndIncrement()

    /** False when no decode is open. The caller must not leave a native abort set. */
    fun abortOpen(): Boolean {
        val latest = next.get() - 1
        if (latest <= 0) return false
        abortedThrough.updateAndGet { current -> maxOf(current, latest) }
        return true
    }

    fun cancelled(id: Int): Boolean = id <= abortedThrough.get()
}

internal interface WhisperEngine {
    fun initContext(modelPath: String): Long
    fun freeContext(contextPtr: Long)
    fun requestAbort()
    fun clearPendingAbort()
    fun transcribe(
        contextPtr: Long,
        audio: FloatArray,
        threads: Int,
        language: String,
        prompt: String,
        beam: Int,
        budgetMs: Long,
    ): String?
}

internal object DecodeMark {
    const val ABORT = "\u001e"

    fun aborted(raw: String?): Boolean = raw != null && raw.startsWith(ABORT)

    fun text(raw: String?): String {
        if (raw == null) return ""
        if (!raw.startsWith(ABORT)) return raw.trim()
        val body = raw.removePrefix(ABORT).trim()
        return body.ifEmpty { TranscriptLimit.UNAVAILABLE }
    }
}

private object JniWhisperEngine : WhisperEngine {
    override fun initContext(modelPath: String): Long = WhisperNative.initContext(modelPath)

    override fun freeContext(contextPtr: Long) {
        WhisperNative.freeContext(contextPtr)
    }

    override fun requestAbort() {
        WhisperNative.requestAbort()
    }

    override fun clearPendingAbort() {
        WhisperNative.clearPendingAbort()
    }

    override fun transcribe(
        contextPtr: Long,
        audio: FloatArray,
        threads: Int,
        language: String,
        prompt: String,
        beam: Int,
        budgetMs: Long,
    ): String? = WhisperNative.transcribe(contextPtr, audio, threads, language, prompt, beam, budgetMs)
}

/**
 * Runs whisper.cpp on one thread. The C context must not be used concurrently,
 * and [release] frees it on that same thread after any in-flight transcription.
 */
internal interface BeamMemory {
    fun load(): Boolean
    fun save(earned: Boolean)

    companion object {
        val NONE: BeamMemory = object : BeamMemory {
            override fun load(): Boolean = false
            override fun save(earned: Boolean) = Unit
        }
    }
}

internal class PrefsBeamMemory(private val prefs: SharedPreferences) : BeamMemory {
    override fun load(): Boolean = prefs.getBoolean(KEY, false)

    override fun save(earned: Boolean) {
        prefs.edit().putBoolean(KEY, earned).commit()
    }

    companion object {
        const val KEY = "beam_earned"
    }
}

class WhisperTranscriber internal constructor(
    private val modelFile: File,
    private val engine: WhisperEngine,
    private val executor: ExecutorService,
    private val memory: BeamMemory = BeamMemory.NONE,
) {
    constructor(modelFile: File) : this(modelFile, JniWhisperEngine, whisperWorker(), BeamMemory.NONE)

    constructor(modelFile: File, prefs: SharedPreferences) : this(
        modelFile,
        JniWhisperEngine,
        whisperWorker(),
        PrefsBeamMemory(prefs),
    )

    private val dispatcher = executor.asCoroutineDispatcher()
    private val released = AtomicBoolean(false)
    private var contextPtr: Long = 0
    private var beamLoaded = false
    private var beamEarned = false
    private val generations = DecodeGeneration()

    fun abort() {
        if (generations.abortOpen()) {
            engine.requestAbort()
        } else {
            engine.clearPendingAbort()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun transcribe(pcm16k: FloatArray, languageCode: String, names: List<String> = emptyList()): String {
        val session = generations.open()
        return try {
            withContext(dispatcher) {
                if (released.get()) error("Speech model was released")
                if (generations.cancelled(session)) {
                    engine.clearPendingAbort()
                    return@withContext TranscriptLimit.UNAVAILABLE
                }
                engine.clearPendingAbort()
                if (generations.cancelled(session)) {
                    return@withContext TranscriptLimit.UNAVAILABLE
                }
                transcribeOpen(pcm16k, names)
            }
        } finally {
            if (generations.cancelled(session)) engine.clearPendingAbort()
        }
    }

    private fun transcribeOpen(pcm16k: FloatArray, names: List<String>): String {
        val audio = SpeechPrep.prepare(pcm16k)
        if (audio.isEmpty()) return ""
        if (contextPtr == 0L) {
            contextPtr = engine.initContext(modelFile.absolutePath)
            if (contextPtr == 0L) {
                error("Could not load the on-device speech model")
            }
        }
        if (!beamLoaded) {
            beamEarned = memory.load()
            beamLoaded = true
        }
        val clipSeconds = audio.size / SpeechPrep.SAMPLE_RATE.toDouble()
        val budgetMs = DecodeBudget.deadlineMs(clipSeconds)
        val beam = BeamSelect.nextBeam(WhisperPrompt.BEAM, beamEarned)
        val started = System.nanoTime()
        val raw = engine.transcribe(
            contextPtr,
            audio,
            WhisperPrompt.THREADS,
            WhisperPrompt.LANGUAGE,
            WhisperPrompt.text(names),
            beam,
            budgetMs,
        )
        val elapsed = (System.nanoTime() - started) / 1_000_000_000.0
        val aborted = DecodeMark.aborted(raw)
        val nextEarned = BeamSelect.remember(beamEarned, beam, elapsed, clipSeconds, aborted)
        if (nextEarned != beamEarned) {
            beamEarned = nextEarned
            memory.save(nextEarned)
        }
        runCatching {
            Log.i(
                "BLINK",
                "decode beam=$beam elapsed_ms=${(elapsed * 1000).toLong()} clip_ms=${(clipSeconds * 1000).toLong()} budget_ms=$budgetMs earned=$beamEarned aborted=$aborted",
            )
        }
        return DecodeMark.text(raw)
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        executor.execute {
            val ptr = contextPtr
            contextPtr = 0
            if (ptr != 0L) {
                try {
                    engine.freeContext(ptr)
                } catch (error: Exception) {
                    Log.w(TAG, "whisper free failed", error)
                }
            }
            executor.shutdown()
        }
    }

    private companion object {
        const val TAG = "WhisperTranscriber"
    }
}

private fun whisperWorker(): ExecutorService = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "saklolo-whisper").apply { priority = Thread.NORM_PRIORITY }
}
