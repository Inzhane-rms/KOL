package ph.appbuilders.saklolo.summary

/**
 * How many times optional Gemma may fail before keyword rules take over.
 *
 * The model loads in the background. An alert never waits on that load.
 * The first load or inference error leaves one retry for a later alert.
 * The second error gives up. An inference timeout is not an error.
 */
class GemmaAttemptPolicy {
    private val lock = Any()
    private var ready = false
    private var loading = false
    private var failures = 0

    val gaveUp: Boolean get() = synchronized(lock) { failures >= MAX_FAILURES }

    /** True only while a load is in flight and the engine is not ready. */
    val showLoading: Boolean get() = synchronized(lock) { loading && !ready }

    /**
     * Returns true for the caller that should run model creation.
     * A second call while that load is in flight does nothing.
     */
    fun beginLoad(): Boolean = synchronized(lock) {
        if (ready || loading || failures >= MAX_FAILURES) return false
        loading = true
        true
    }

    fun finishLoad(success: Boolean) = synchronized(lock) {
        loading = false
        if (success) {
            ready = true
            failures = 0
        } else if (!ready) {
            failures += 1
        }
    }

    fun noteInferenceSuccess() = synchronized(lock) {
        if (ready) failures = 0
    }

    fun noteInferenceFailure() = synchronized(lock) {
        failures += 1
    }

    /** A slow generateResponse is not a failed attempt and does not burn the retry. */
    fun noteInferenceTimeout() = synchronized(lock) {
    }

    fun plan(eligible: Boolean, engineReady: Boolean): GemmaRefinePlan = synchronized(lock) {
        if (!eligible || failures >= MAX_FAILURES) return GemmaRefinePlan.SKIP
        if (!engineReady) return GemmaRefinePlan.RULES_WITHOUT_WAITING
        GemmaRefinePlan.INFER
    }

    companion object {
        const val MAX_FAILURES = 2
        const val RAM_FLOOR_BYTES = 3_400L * 1024L * 1024L
        const val INFERENCE_TIMEOUT_SECONDS = 15L
    }
}

enum class GemmaRefinePlan {
    /** No file, low RAM, or two failures. Keep the keyword line. */
    SKIP,

    /** Load is still running, or a retry was just started. Do not block the alert. */
    RULES_WITHOUT_WAITING,

    /** Engine is ready. The 15 second timeout wraps this call only. */
    INFER,
}
