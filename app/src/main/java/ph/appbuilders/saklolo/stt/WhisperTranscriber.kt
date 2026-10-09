package ph.appbuilders.saklolo.stt

import android.util.Log
import java.io.File
import ph.appbuilders.saklolo.audio.SpeechPrep
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

internal interface WhisperEngine {
    fun initContext(modelPath: String): Long
    fun freeContext(contextPtr: Long)
    fun transcribe(contextPtr: Long, audio: FloatArray, threads: Int, language: String, prompt: String, beam: Int): String?
}

private object JniWhisperEngine : WhisperEngine {
    override fun initContext(modelPath: String): Long = WhisperNative.initContext(modelPath)

    override fun freeContext(contextPtr: Long) {
        WhisperNative.freeContext(contextPtr)
    }

    override fun transcribe(
        contextPtr: Long,
        audio: FloatArray,
        threads: Int,
        language: String,
        prompt: String,
        beam: Int,
    ): String? = WhisperNative.transcribe(contextPtr, audio, threads, language, prompt, beam)
}

/**
 * Runs whisper.cpp on one thread. The C context must not be used concurrently,
 * and [release] frees it on that same thread after any in-flight transcription.
 */
class WhisperTranscriber internal constructor(
    private val modelFile: File,
    private val engine: WhisperEngine,
    private val executor: ExecutorService,
) {
    constructor(modelFile: File) : this(modelFile, JniWhisperEngine, whisperWorker())

    private val dispatcher = executor.asCoroutineDispatcher()
    private val released = AtomicBoolean(false)
    private var contextPtr: Long = 0
    private var beamSlow = false

    @Suppress("UNUSED_PARAMETER")
    suspend fun transcribe(pcm16k: FloatArray, languageCode: String, names: List<String> = emptyList()): String = withContext(dispatcher) {
        if (released.get()) error("Speech model was released")
        val audio = SpeechPrep.prepare(pcm16k)
        if (audio.isEmpty()) return@withContext ""
        if (contextPtr == 0L) {
            contextPtr = engine.initContext(modelFile.absolutePath)
            if (contextPtr == 0L) {
                error("Could not load the on-device speech model")
            }
        }
        val clipSeconds = audio.size / SpeechPrep.SAMPLE_RATE.toDouble()
        val beam = if (DecodeBudget.allowBeam(WhisperPrompt.BEAM, beamSlow)) WhisperPrompt.BEAM else 1
        val started = System.nanoTime()
        val text = engine.transcribe(
            contextPtr,
            audio,
            WhisperPrompt.THREADS,
            WhisperPrompt.LANGUAGE,
            WhisperPrompt.text(names),
            beam,
        )
        val elapsed = (System.nanoTime() - started) / 1_000_000_000.0
        if (beam > 1 && DecodeBudget.markSlow(elapsed, clipSeconds)) beamSlow = true
        text?.trim().orEmpty()
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
