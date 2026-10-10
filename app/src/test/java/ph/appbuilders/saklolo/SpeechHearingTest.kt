package ph.appbuilders.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.contact.Ptt
import ph.appbuilders.saklolo.stt.BeamSelect
import ph.appbuilders.saklolo.stt.CaptionCleanup
import ph.appbuilders.saklolo.stt.DecodeBudget
import ph.appbuilders.saklolo.stt.DecodeMark
import ph.appbuilders.saklolo.stt.SpeechHearing
import ph.appbuilders.saklolo.stt.TranscriptLimit
import ph.appbuilders.saklolo.stt.SpeechLexicon
import ph.appbuilders.saklolo.stt.SpeechPolish
import ph.appbuilders.saklolo.stt.SpeechScore
import ph.appbuilders.saklolo.stt.WhisperPrompt
import ph.appbuilders.saklolo.triage.TriageEngine
import ph.appbuilders.saklolo.triage.Urgency

class SpeechHearingTest {
    @Test
    fun shortTokensAndPunctuationStayPut() {
        assertEquals("si ana ok", SpeechLexicon.correct("si ana ok"))
        assertEquals("sige", SpeechLexicon.correct("sige"))
        assertEquals("hindi", SpeechLexicon.correct("hindi"))
        assertEquals("nahimatay,", SpeechLexicon.correct("nahimatax,"))
        assertEquals("mediqq", SpeechLexicon.correct("mediqq"))
        assertEquals("medic", SpeechLexicon.correct("medik"))
    }

    @Test
    fun longerTokensAllowScaledEditsAndTiesStay() {
        assertEquals("nahimatyx", SpeechLexicon.correct("nahimatyx"))
        assertNull(
            SpeechLexicon.match(
                "abcde",
                listOf("abcdf" to "One", "abcdx" to "Two"),
            ),
        )
    }

    @Test
    fun emergencyWordsAreNotRewrittenAndLooseWordsAreNotPromoted() {
        assertEquals("nahimatay", SpeechLexicon.correct("nahimatay", listOf("Nahimata")))
        assertEquals("hello", SpeechLexicon.correct("hello"))
        assertEquals("nandito", SpeechLexicon.correct("nandito"))
        assertFalse(SpeechLexicon.veryClose("nandito", "nahimatay"))
        assertTrue(SpeechLexicon.veryClose("nahimatax", "nahimatay"))
        assertEquals("tulong", SpeechLexicon.correct("tulon"))
    }

    @Test
    fun contactNamesKeepTheirCasing() {
        assertEquals("Maria", SpeechLexicon.correct("mario", listOf("Maria Santos")))
        assertTrue(WhisperPrompt.text(listOf("Maria Santos")).endsWith("Maria Santos."))
        assertTrue(WhisperPrompt.text(emptyList()).contains("kita tayo"))
    }

    @Test
    fun gatesNumbersFillersAndSentenceCase() {
        assertEquals("Nandito ako sa Gate 3", SpeechPolish.apply("nandito ako sa gate tres"))
        assertEquals("Gate 3", SpeechPolish.apply("gate three"))
        assertEquals("Nahimatay", SpeechPolish.apply("[Music] Thank you. nahimatay"))
        assertEquals("Nahimatay", SpeechPolish.apply("nahimatay nahimatay nahimatay"))
        assertEquals("VIP sa entrance", SpeechPolish.apply("vip sa entrance"))
    }

    @Test
    fun correctedLineAndRawLineTakeTheHigherUrgency() {
        val heard = SpeechHearing.interpret("nahimatax sa gate")
        assertTrue(heard.shown.contains("Nahimatay"))
        assertEquals(Urgency.CRITICAL, heard.urgency)
        assertTrue(heard.emergency)
        val safe = TriageEngine.triage("nandito ako")
        val critical = TriageEngine.triage("nahimatay")
        assertEquals(Urgency.CRITICAL, SpeechHearing.higher(safe, critical).urgency)
        assertTrue(Ptt.either("nandito ako", "nahimatay"))
        assertFalse(Ptt.either("nandito ako", null))
    }

    @Test
    fun gemmaCleanupSkipsEmergencies() {
        assertFalse(CaptionCleanup.allowModel(Urgency.CRITICAL, "nahimatay", "Nahimatay"))
        assertFalse(CaptionCleanup.allowModel(Urgency.NEEDS_HELP, "tulong", "Tulong"))
        assertTrue(CaptionCleanup.allowModel(Urgency.NEEDS_HELP, "nandito ako sa gate", "Nandito ako sa gate"))
        assertFalse(CaptionCleanup.acceptModel("nandito ako", "nahimatay sa gate"))
        assertTrue(CaptionCleanup.acceptModel("nandito ako sa gate", "Nandito ako sa gate."))
    }

    @Test
    fun beamFallsBackWhenAClipRunsLongerThanItself() {
        assertEquals(5, WhisperPrompt.BEAM)
        assertFalse(WhisperPrompt.USE_BEAM)
        assertFalse(DecodeBudget.allowBeam(WhisperPrompt.BEAM, earnedFast = false))
        assertTrue(DecodeBudget.allowBeam(WhisperPrompt.BEAM, earnedFast = true))
        assertFalse(DecodeBudget.allowBeam(1, earnedFast = true))
        assertTrue(DecodeBudget.markFast(elapsedSeconds = 0.4, clipSeconds = 1.0))
        assertFalse(DecodeBudget.markFast(elapsedSeconds = 1.2, clipSeconds = 1.0))
        assertEquals(15_000L, DecodeBudget.deadlineMs(1.0))
        assertEquals(40_000L, DecodeBudget.deadlineMs(10.0))
        assertEquals("nandito ako", DecodeMark.text("\u001enandito ako"))
        assertEquals(TranscriptLimit.UNAVAILABLE, DecodeMark.text("\u001e"))
        assertFalse(DecodeBudget.markSlow(elapsedSeconds = 10.0, clipSeconds = 10.0))
        assertTrue(DecodeBudget.markSlow(elapsedSeconds = 16.0, clipSeconds = 10.0))
        assertEquals(1, BeamSelect.nextBeam(WhisperPrompt.BEAM, earnedFast = false))
        assertEquals(5, BeamSelect.nextBeam(WhisperPrompt.BEAM, earnedFast = true))
        assertEquals(1, BeamSelect.nextBeam(1, earnedFast = true))
        assertTrue(BeamSelect.remember(wasEarned = false, beam = 1, elapsedSeconds = 0.4, clipSeconds = 1.0, aborted = false))
        assertFalse(BeamSelect.remember(wasEarned = false, beam = 1, elapsedSeconds = 1.2, clipSeconds = 1.0, aborted = false))
        assertFalse(BeamSelect.remember(wasEarned = true, beam = 5, elapsedSeconds = 2.0, clipSeconds = 1.0, aborted = false))
        assertFalse(BeamSelect.remember(wasEarned = true, beam = 5, elapsedSeconds = 0.2, clipSeconds = 1.0, aborted = true))
        assertTrue(BeamSelect.remember(wasEarned = true, beam = 5, elapsedSeconds = 0.2, clipSeconds = 1.0, aborted = false))
        val unavailable = SpeechHearing.interpret("transcript unavailable")
        assertEquals("transcript unavailable", unavailable.shown)
        assertEquals("transcript unavailable", unavailable.raw)
    }

    @Test
    fun samplePhrasesImproveOnTheUncorrectedText() {
        val before = SpeechScore.report(SAMPLES) { it }
        val after = SpeechScore.report(SAMPLES) { SpeechPolish.apply(it) }
        assertTrue(after.exact > before.exact)
        assertTrue(after.wordHits >= before.wordHits)
        assertEquals(SAMPLES.size, after.total)
        assertTrue(after.exact >= 8)
    }

    companion object {
        val SAMPLES = listOf(
            "nahimatax sa gate" to "Nahimatay sa gate",
            "naipet sa exit" to "Naipit sa exit",
            "siksikan sa entrance" to "Siksikan sa entrance",
            "nandito ako sa gate tres" to "Nandito ako sa Gate 3",
            "gate three" to "Gate 3",
            "papunta na ako" to "Papunta na ako",
            "[Music] Thank you." to "",
            "kita tayo sa stage" to "Kita tayo sa stage",
            "hello friend" to "Hello friend",
            "nahimatay" to "Nahimatay",
            "saan ang medic" to "Saan ang medic",
            "nawawala sa barricade" to "Nawawala sa barricade",
            "vip sa cr" to "VIP sa CR",
            "hinde makahinga" to "Hindi makahinga",
            "frist aid sa stage" to "First aid sa stage",
        )
    }
}
