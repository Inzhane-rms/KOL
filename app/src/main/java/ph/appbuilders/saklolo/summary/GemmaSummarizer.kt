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
import ph.appbuilders.saklolo.ask.AskEngine
import ph.appbuilders.saklolo.triage.SummaryChoice
import ph.appbuilders.saklolo.triage.SummaryRefine

/**
 * Optional Gemma 3 1B int4 summary via MediaPipe LLM Inference.
 * The `.task` file is sideloaded, never bundled. Keyword rules stay the default
 * when the file is missing, RAM is short, the call errors, or 15 seconds pass.
 * Urgency is not decided here.
 *
 * Advertised 4 GB phones often report about 3.4–3.8 GiB. The gate is 3.4 GiB
 * so a Camon 40 still qualifies and a 3 GB phone does not.
 */
object GemmaSummarizer {
    private const val TAG = "SakloloGemma"
    private const val FILE_NAME = "gemma3-1b-it-int4.task"
    private val RAM_FLOOR = 3_400L * 1024L * 1024L
    private const val TIMEOUT_SECONDS = 15L

    private val executor = Executors.newSingleThreadExecutor()
    private val loadLock = Any()
    private var engine: LlmInference? = null
    private var unavailable = false

    fun mightRun(context: Context): Boolean {
        if (unavailable) return false
        return findModel(context) != null && hasEnoughRam(context)
    }

    fun describe(context: Context): String {
        val file = findModel(context)
        if (file == null) return "No Gemma file on this phone. Summaries use keyword rules."
        if (!hasEnoughRam(context)) {
            return "Gemma file found, but this phone reports under about 4 GB of RAM. Summaries use keyword rules."
        }
        if (unavailable) return "Gemma file found, but it did not load. Summaries use keyword rules."
        if (engine != null) return "Gemma 3 1B is loaded. It can refine the one-line summary."
        return "Gemma file found at ${file.parentFile?.name ?: "files"}. It loads on the next alert."
    }

    /**
     * Picks a stored Ask B-LINK pair id, or null for NONE / anything that is not
     * a valid id. The model string is never returned to the screen.
     */
    fun chooseAskPair(context: Context, question: String, catalog: String, validIds: Set<Int>): Int? {
        if (!mightRun(context)) return null
        val future = executor.submit<Int?> {
            try {
                val llm = ensureEngine(context) ?: return@submit null
                AskEngine.parsePairChoice(askIndex(llm, question, catalog), validIds)
            } catch (error: Exception) {
                Log.w(TAG, "ask index failed", error)
                null
            }
        }
        return try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            null
        } catch (error: Exception) {
            Log.w(TAG, "ask index wait failed", error)
            null
        }
    }

    fun refine(context: Context, transcript: String, rulesSummary: String): SummaryChoice {
        val rules = SummaryChoice(rulesSummary, SummaryRefine.RULES)
        if (!mightRun(context)) return rules
        val future = executor.submit<SummaryChoice> {
            try {
                val llm = ensureEngine(context) ?: return@submit rules
                SummaryRefine.choose(rulesSummary, ask(llm, transcript))
            } catch (error: Exception) {
                Log.w(TAG, "summary failed", error)
                unavailable = true
                rules
            }
        }
        return try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            rules
        } catch (error: Exception) {
            Log.w(TAG, "summary wait failed", error)
            rules
        }
    }

    private fun ensureEngine(context: Context): LlmInference? {
        engine?.let { return it }
        synchronized(loadLock) {
            engine?.let { return it }
            if (unavailable) return null
            val file = findModel(context) ?: return null
            return try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(file.absolutePath)
                    .setMaxTokens(1280)
                    .build()
                LlmInference.createFromOptions(context.applicationContext, options).also { engine = it }
            } catch (error: Exception) {
                Log.w(TAG, "model load failed", error)
                unavailable = true
                null
            }
        }
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

    private fun askIndex(llm: LlmInference, question: String, catalog: String): String {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(1)
            .setTemperature(0f)
            .build()
        val session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
        return try {
            session.addQueryChunk(
                """
                Match the question to one stored safety pair.
                Reply with one token only: the pair id, or NONE.
                Do not write an answer.
                Question: ${question.take(400)}
                Pairs:
                $catalog
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

    private fun findModel(context: Context): File? {
        val external = context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) }
        val internal = File(context.filesDir, FILE_NAME)
        return listOfNotNull(external, internal).firstOrNull { it.isFile && it.length() > 0L }
    }

    private fun hasEnoughRam(context: Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.totalMem >= RAM_FLOOR
    }
}
