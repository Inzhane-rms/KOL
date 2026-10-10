package ph.appbuilders.saklolo.summary

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ph.appbuilders.saklolo.triage.SummaryChoice
import ph.appbuilders.saklolo.triage.SummaryRefine

/**
 * Optional Gemma 3 1B int4 summary via MediaPipe LLM Inference.
 * The `.task` file is sideloaded, never bundled. Keyword rules stay the default
 * when the file is missing, RAM is short, two attempts fail, or inference
 * takes longer than 15 seconds. Urgency is not decided here.
 *
 * Load starts in the background at app start when the file exists and RAM is
 * at least 3.4 GiB. The 15 second limit wraps [generateResponse] only, so the
 * first alert is not stuck behind model creation. One failure is retried on
 * the next alert. The second failure stops further attempts.
 *
 * Advertised 4 GB phones often report about 3.4–3.8 GiB. The gate is 3.4 GiB
 * so a Camon 40 still qualifies and a 3 GB phone does not.
 */
object GemmaSummarizer {
    private const val TAG = "SakloloGemma"
    private const val FILE_NAME = "gemma3-1b-it-int4.task"
    private const val REPLY_TIMEOUT_SECONDS = 8L

    private val policy = GemmaAttemptPolicy()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "saklolo-gemma")
    }
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    @Volatile
    private var engine: LlmInference? = null

    fun preload(context: Context) {
        if (!eligible(context)) return
        if (!policy.beginLoad()) return
        publish()
        val appContext = context.applicationContext
        executor.execute {
            var ok = false
            try {
                ok = createEngine(appContext)
            } catch (error: Throwable) {
                Log.w(TAG, "model load failed", error)
            } finally {
                policy.finishLoad(ok)
                publish()
            }
        }
    }

    fun mightRun(context: Context): Boolean {
        if (policy.gaveUp) return false
        return eligible(context)
    }

    /** True when a summary call can run now, without waiting for a load. */
    fun isReady(): Boolean = engine != null && !policy.gaveUp

    fun describe(context: Context): String {
        val file = findModel(context)
        if (file == null) return "No Gemma file on this phone. Summaries use keyword rules."
        if (!hasEnoughRam(context)) {
            return "Gemma file found, but this phone reports under about 4 GB of RAM. Summaries use keyword rules."
        }
        if (policy.gaveUp) return "Gemma hit an error twice. Summaries use keyword rules."
        if (engine != null) return "Gemma 3 1B is loaded. It can refine the one-line summary."
        if (policy.showLoading) return "Gemma is loading in the background."
        return "Gemma file found at ${file.parentFile?.name ?: "files"}. It loads in the background at app start."
    }

    /**
     * Two or three short reply lines, or null.
     * Runs only when Gemma is already loaded and no other inference is in flight.
     * A timeout or a bad reply returns null so the rules stay on screen.
     */
    fun suggestReplies(heard: String): String? {
        if (heard.isBlank() || engine == null || policy.gaveUp) return null
        if (!policy.tryBeginInference()) return null
        val llm = engine ?: run {
            policy.finishInference()
            return null
        }
        val future = try {
            executor.submit<String?> {
                try {
                    askReplies(llm, heard)
                } catch (error: Exception) {
                    Log.w(TAG, "reply suggestion failed", error)
                    null
                } finally {
                    policy.finishInference()
                }
            }
        } catch (error: Throwable) {
            policy.finishInference()
            Log.w(TAG, "reply suggestion submit failed", error)
            return null
        }
        return try {
            future.get(REPLY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            policy.noteInferenceTimeout()
            future.cancel(true)
            null
        } catch (error: Exception) {
            Log.w(TAG, "reply suggestion wait failed", error)
            null
        }
    }

    /**
     * Light wording cleanup. Call only after the rules, and only for text that
     * is not an emergency. Returns null when Gemma is not already loaded, is busy,
     * or does not answer within 4 seconds.
     */
    fun cleanupCaption(text: String): String? {
        if (text.isBlank() || engine == null || policy.gaveUp) return null
        if (!policy.tryBeginInference()) return null
        val llm = engine ?: run {
            policy.finishInference()
            return null
        }
        val future = try {
            executor.submit<String?> {
                try {
                    askCleanup(llm, text).trim().lineSequence().firstOrNull()?.trim()
                } catch (error: Exception) {
                    Log.w(TAG, "caption cleanup failed", error)
                    null
                } finally {
                    policy.finishInference()
                }
            }
        } catch (error: Throwable) {
            policy.finishInference()
            Log.w(TAG, "caption cleanup submit failed", error)
            return null
        }
        return try {
            future.get(4, TimeUnit.SECONDS)?.takeIf { it.isNotEmpty() }
        } catch (_: TimeoutException) {
            policy.noteInferenceTimeout()
            future.cancel(true)
            null
        } catch (error: Exception) {
            Log.w(TAG, "caption cleanup wait failed", error)
            null
        }
    }

    fun refine(context: Context, transcript: String, rulesSummary: String): SummaryChoice {
        val rules = SummaryChoice(rulesSummary, SummaryRefine.RULES)
        val llm = engine
        return when (policy.plan(eligible(context), llm != null)) {
            GemmaRefinePlan.SKIP -> rules
            GemmaRefinePlan.RULES_WITHOUT_WAITING -> {
                preload(context)
                rules
            }
            GemmaRefinePlan.INFER -> {
                val ready = llm ?: return rules
                infer(rules) {
                    SummaryRefine.choose(rulesSummary, ask(ready, transcript))
                }
            }
        }
    }

    private fun <T> infer(fallback: T, block: () -> T): T {
        if (!policy.tryBeginInference()) return fallback
        val timedOut = AtomicBoolean(false)
        val future = try {
            executor.submit<T> {
                try {
                    val value = block()
                    if (!timedOut.get()) policy.noteInferenceSuccess()
                    value
                } catch (error: Exception) {
                    Log.w(TAG, "inference failed", error)
                    if (!timedOut.get()) policy.noteInferenceFailure()
                    fallback
                } finally {
                    policy.finishInference()
                }
            }
        } catch (error: Throwable) {
            policy.finishInference()
            Log.w(TAG, "inference submit failed", error)
            return fallback
        }
        return try {
            future.get(GemmaAttemptPolicy.INFERENCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            timedOut.set(true)
            policy.noteInferenceTimeout()
            future.cancel(true)
            fallback
        } catch (error: Exception) {
            Log.w(TAG, "inference wait failed", error)
            fallback
        }
    }

    private fun createEngine(context: Context): Boolean {
        if (engine != null) return true
        val file = findModel(context) ?: return false
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(file.absolutePath)
            .setMaxTokens(1280)
            .build()
        engine = LlmInference.createFromOptions(context, options)
        return engine != null
    }

    private fun ask(llm: LlmInference, transcript: String): String {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(40)
            .setTemperature(0.2f)
            .build()
        val session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
        return try {
            session.addQueryChunk(prompt(transcript))
            session.generateResponse()
        } finally {
            session.close()
        }
    }

    private fun askReplies(llm: LlmInference, heard: String): String {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(1)
            .setTemperature(0f)
            .build()
        val session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
        return try {
            session.addQueryChunk(
                """
                Write 2 or 3 short Tagalog replies to the message.
                One reply per line. No numbers. No explanation.
                Each line under 40 characters.
                Message: ${heard.take(240)}
                """.trimIndent(),
            )
            session.generateResponse()
        } finally {
            session.close()
        }
    }

    private fun askCleanup(llm: LlmInference, text: String): String {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(1)
            .setTemperature(0f)
            .build()
        val session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
        return try {
            session.addQueryChunk(
                """
                Fix spelling, casing, and punctuation in this caption.
                Keep the same meaning. Do not add facts. Do not translate.
                One line only.
                Caption: ${text.take(400)}
                """.trimIndent(),
            )
            session.generateResponse()
        } finally {
            session.close()
        }
    }

    private fun prompt(transcript: String): String = """
        You are the on-device summary for an offline disaster SOS app.
        Rewrite the transcript as one short English line for a responder.
        Keep counts, needs, and place names that are actually in the transcript.
        Do not invent facts. Do not assign an urgency label. One line only.
        Transcript: ${transcript.take(800)}
    """.trimIndent()

    private fun publish() {
        _loading.value = policy.showLoading
    }

    private fun eligible(context: Context): Boolean =
        findModel(context) != null && hasEnoughRam(context)

    private fun findModel(context: Context): File? {
        val external = context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) }
        val internal = File(context.filesDir, FILE_NAME)
        return listOfNotNull(external, internal).firstOrNull { it.isFile && it.length() > 0L }
    }

    private fun hasEnoughRam(context: Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.totalMem >= GemmaAttemptPolicy.RAM_FLOOR_BYTES
    }
}
