package ph.appbuilders.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.contact.Ptt
import ph.appbuilders.saklolo.stt.SpeechHearing
import ph.appbuilders.saklolo.stt.SpeechLexicon
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
    fun longerTokensAllowTwoEditsAndTiesStay() {
        assertEquals("nahimatay", SpeechLexicon.correct("nahimatyx"))
        assertNull(
            SpeechLexicon.match(
                "abcde",
                listOf("abcdf" to "One", "abcdx" to "Two"),
            ),
        )
    }

    @Test
    fun contactNamesKeepTheirCasing() {
        assertEquals("Maria", SpeechLexicon.correct("mario", listOf("Maria Santos")))
        assertTrue(WhisperPrompt.text(listOf("Maria Santos")).endsWith("Maria Santos."))
        assertEquals(WhisperPrompt.TEXT, WhisperPrompt.text(emptyList()))
    }

    @Test
    fun correctedLineAndRawLineTakeTheHigherUrgency() {
        val heard = SpeechHearing.interpret("nahimatax sa gate")
        assertTrue(heard.shown.contains("nahimatay"))
        assertEquals(Urgency.CRITICAL, heard.urgency)
        assertTrue(heard.emergency)
        val safe = TriageEngine.triage("nandito ako")
        val critical = TriageEngine.triage("nahimatay")
        assertEquals(Urgency.CRITICAL, SpeechHearing.higher(safe, critical).urgency)
        assertEquals(Urgency.CRITICAL, SpeechHearing.higher(critical, safe).urgency)
        assertTrue(Ptt.either("nandito ako", "nahimatay"))
        assertFalse(Ptt.either("nandito ako", null))
    }
}
