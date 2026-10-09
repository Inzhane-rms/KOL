package ph.appbuilders.saklolo.stt

import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Runs whisper.cpp on one thread. The C context must not be used concurrently.
 */
class WhisperTranscriber(private val modelFile: File) {
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "saklolo-whisper").apply { priority = Thread.NORM_PRIORITY }
    }.asCoroutineDispatcher()
    private var contextPtr: Long = 0

    suspend fun transcribe(pcm16k: FloatArray, languageCode: String): String = withContext(dispatcher) {
        if (contextPtr == 0L) {
            contextPtr = WhisperNative.initContext(modelFile.absolutePath)
            if (contextPtr == 0L) {
                error("Could not load the on-device speech model")
            }
        }
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        WhisperNative.transcribe(contextPtr, pcm16k, threads, languageCode)
            ?.trim()
            .orEmpty()
    }

    fun release() {
        val ptr = contextPtr
        contextPtr = 0
        if (ptr != 0L) {
            WhisperNative.freeContext(ptr)
        }
    }
}
