package ph.appbuilders.saklolo

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.stt.DecodeMark
import ph.appbuilders.saklolo.stt.TranscriptLimit
import ph.appbuilders.saklolo.stt.WhisperEngine
import ph.appbuilders.saklolo.stt.WhisperTranscriber

class WhisperReleaseTest {
    @Test
    fun releaseFreesOnTheWorkerAfterInFlightTranscription() = runBlocking {
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val freed = CountDownLatch(1)
        val freeThread = AtomicReference<Thread>()
        val order = mutableListOf<String>()
        val engine = object : WhisperEngine {
            override fun initContext(modelPath: String): Long {
                order += "init"
                return 7L
            }

            override fun freeContext(contextPtr: Long) {
                order += "free:$contextPtr"
                freeThread.set(Thread.currentThread())
                freed.countDown()
            }

            override fun requestAbort() = Unit

            override fun clearPendingAbort() = Unit

            override fun transcribe(
                contextPtr: Long,
                audio: FloatArray,
                threads: Int,
                language: String,
                prompt: String,
                beam: Int,
                budgetMs: Long,
            ): String {
                order += "transcribe"
                started.countDown()
                assertTrue(finish.await(5, TimeUnit.SECONDS))
                order += "transcribe-done"
                return "tabang"
            }
        }
        val worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "saklolo-whisper")
        }
        val transcriber = WhisperTranscriber(File("model.bin"), engine, worker)
        val job = launch(Dispatchers.Default) {
            assertEquals("tabang", transcriber.transcribe(floatArrayOf(0.2f), "tl"))
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        val caller = Thread.currentThread()
        transcriber.release()
        transcriber.release()
        Thread.sleep(150)
        assertEquals(1L, freed.count)
        finish.countDown()
        assertTrue(freed.await(5, TimeUnit.SECONDS))
        job.join()
        assertEquals("saklolo-whisper", freeThread.get().name)
        assertNotSame(caller, freeThread.get())
        assertTrue(order.indexOf("transcribe-done") < order.indexOf("free:7"))
        assertEquals(1, order.count { it.startsWith("free:") })
    }

    @Test
    fun abortThenTheNextTranscriptionSucceeds() = runBlocking {
        val calls = AtomicInteger(0)
        val nativeAborts = AtomicInteger(0)
        val engine = object : WhisperEngine {
            override fun initContext(modelPath: String): Long = 1L
            override fun freeContext(contextPtr: Long) = Unit
            override fun requestAbort() {
                nativeAborts.incrementAndGet()
            }
            override fun clearPendingAbort() = Unit
            override fun transcribe(
                contextPtr: Long,
                audio: FloatArray,
                threads: Int,
                language: String,
                prompt: String,
                beam: Int,
                budgetMs: Long,
            ): String {
                calls.incrementAndGet()
                return "tabang"
            }
        }
        val worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "saklolo-whisper")
        }
        val transcriber = WhisperTranscriber(File("model.bin"), engine, worker)
        transcriber.abort()
        assertEquals("tabang", transcriber.transcribe(floatArrayOf(0.2f), "tl"))
        assertEquals(1, calls.get())
        assertEquals(0, nativeAborts.get())
        worker.shutdown()
    }

    @Test
    fun abortMidDecodeCancelsThatDecodeAndTheNextOneRuns() = runBlocking {
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val nativeAbort = AtomicInteger(0)
        val calls = AtomicInteger(0)
        val engine = object : WhisperEngine {
            override fun initContext(modelPath: String): Long = 1L
            override fun freeContext(contextPtr: Long) = Unit
            override fun requestAbort() {
                nativeAbort.set(1)
            }
            override fun clearPendingAbort() {
                nativeAbort.set(0)
            }
            override fun transcribe(
                contextPtr: Long,
                audio: FloatArray,
                threads: Int,
                language: String,
                prompt: String,
                beam: Int,
                budgetMs: Long,
            ): String {
                val call = calls.incrementAndGet()
                if (call == 1) {
                    started.countDown()
                    assertTrue(finish.await(5, TimeUnit.SECONDS))
                    return if (nativeAbort.get() == 1) DecodeMark.ABORT else "tabang"
                }
                return "tabang"
            }
        }
        val worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "saklolo-whisper")
        }
        val transcriber = WhisperTranscriber(File("model.bin"), engine, worker)
        val first = async(Dispatchers.Default) { transcriber.transcribe(floatArrayOf(0.2f), "tl") }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        transcriber.abort()
        finish.countDown()
        assertEquals(TranscriptLimit.UNAVAILABLE, first.await())
        assertEquals("tabang", transcriber.transcribe(floatArrayOf(0.2f), "tl"))
        assertEquals(2, calls.get())
        worker.shutdown()
    }
}
