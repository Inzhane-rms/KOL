package ph.appbuilders.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.summary.GemmaAttemptPolicy
import ph.appbuilders.saklolo.summary.GemmaRefinePlan

class GemmaAttemptPolicyTest {
    @Test
    fun ramFloorIsThreePointFourGibibyteAndInferenceTimeoutIsFifteenSeconds() {
        assertEquals(3_400L * 1024L * 1024L, GemmaAttemptPolicy.RAM_FLOOR_BYTES)
        assertEquals(15L, GemmaAttemptPolicy.INFERENCE_TIMEOUT_SECONDS)
        assertEquals(2, GemmaAttemptPolicy.MAX_FAILURES)
    }

    @Test
    fun loadInFlightDoesNotStartASecondLoadAndAlertDoesNotWait() {
        val policy = GemmaAttemptPolicy()
        assertTrue(policy.beginLoad())
        assertTrue(policy.showLoading)
        assertFalse(policy.beginLoad())
        assertEquals(GemmaRefinePlan.RULES_WITHOUT_WAITING, policy.plan(eligible = true, engineReady = false))
    }

    @Test
    fun successfulLoadClearsTheLoadingFlagAndAllowsInference() {
        val policy = GemmaAttemptPolicy()
        assertTrue(policy.beginLoad())
        policy.finishLoad(success = true)
        assertFalse(policy.showLoading)
        assertFalse(policy.gaveUp)
        assertEquals(GemmaRefinePlan.INFER, policy.plan(eligible = true, engineReady = true))
    }

    @Test
    fun oneLoadFailureLeavesARetryAndTheSecondGivesUp() {
        val policy = GemmaAttemptPolicy()
        assertTrue(policy.beginLoad())
        policy.finishLoad(success = false)
        assertFalse(policy.showLoading)
        assertFalse(policy.gaveUp)
        assertEquals(GemmaRefinePlan.RULES_WITHOUT_WAITING, policy.plan(eligible = true, engineReady = false))
        assertTrue(policy.beginLoad())
        policy.finishLoad(success = false)
        assertTrue(policy.gaveUp)
        assertFalse(policy.beginLoad())
        assertEquals(GemmaRefinePlan.SKIP, policy.plan(eligible = true, engineReady = false))
    }

    @Test
    fun oneInferenceFailureIsRetriedAndTheSecondGivesUp() {
        val policy = readyPolicy()
        policy.noteInferenceFailure()
        assertFalse(policy.gaveUp)
        assertEquals(GemmaRefinePlan.INFER, policy.plan(eligible = true, engineReady = true))
        policy.noteInferenceFailure()
        assertTrue(policy.gaveUp)
        assertEquals(GemmaRefinePlan.SKIP, policy.plan(eligible = true, engineReady = true))
    }

    @Test
    fun inferenceTimeoutDoesNotBurnTheRetry() {
        val policy = readyPolicy()
        policy.noteInferenceFailure()
        policy.noteInferenceTimeout()
        policy.noteInferenceTimeout()
        assertFalse(policy.gaveUp)
        assertFalse(policy.showLoading)
        assertEquals(GemmaRefinePlan.INFER, policy.plan(eligible = true, engineReady = true))
        policy.noteInferenceFailure()
        assertTrue(policy.gaveUp)
    }

    @Test
    fun inferenceSuccessClearsAPreviousFailure() {
        val policy = readyPolicy()
        policy.noteInferenceFailure()
        policy.noteInferenceSuccess()
        policy.noteInferenceFailure()
        assertFalse(policy.gaveUp)
        assertEquals(GemmaRefinePlan.INFER, policy.plan(eligible = true, engineReady = true))
    }

    @Test
    fun aRunningInferenceIsNotStartedAgain() {
        val policy = readyPolicy()
        assertTrue(policy.tryBeginInference())
        assertTrue(policy.isInferenceRunning())
        assertFalse(policy.tryBeginInference())
        policy.noteInferenceTimeout()
        assertTrue(policy.isInferenceRunning())
        assertFalse(policy.gaveUp)
        policy.finishInference()
        assertFalse(policy.isInferenceRunning())
        assertTrue(policy.tryBeginInference())
    }

    @Test
    fun missingFileOrLowRamSkipsEvenWhenTheEngineExists() {
        val policy = readyPolicy()
        assertEquals(GemmaRefinePlan.SKIP, policy.plan(eligible = false, engineReady = true))
    }

    private fun readyPolicy(): GemmaAttemptPolicy {
        val policy = GemmaAttemptPolicy()
        assertTrue(policy.beginLoad())
        policy.finishLoad(success = true)
        return policy
    }
}
