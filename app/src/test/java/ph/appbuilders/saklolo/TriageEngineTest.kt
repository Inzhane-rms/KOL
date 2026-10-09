package ph.appbuilders.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.triage.TriageEngine
import ph.appbuilders.saklolo.triage.Urgency

class TriageEngineTest {
    @Test
    fun tagalogTrappedNeedWaterPurok() {
        val result = TriageEngine.triage("Tatlong tao ang naipit sa Purok 4, kailangan ng tubig")
        assertEquals(Urgency.CRITICAL, result.urgency)
        assertEquals("3 trapped, need water, Purok 4", result.summary)
        assertTrue(result.actionable)
    }

    @Test
    fun englishPhraseMatchesTheSameSummary() {
        val result = TriageEngine.triage("3 trapped, need water, Purok 4")
        assertEquals(Urgency.CRITICAL, result.urgency)
        assertEquals("3 trapped, need water, Purok 4", result.summary)
    }

    @Test
    fun purokNumberIsNotAHeadcount() {
        val result = TriageEngine.triage("Naipit sa Purok 4")
        assertEquals(Urgency.CRITICAL, result.urgency)
        assertEquals("trapped, need rescue, Purok 4", result.summary)
    }

    @Test
    fun bisayaChildTrappedNeedsWater() {
        val result = TriageEngine.triage("Usa ka bata ang napiit sa balay sa Purok 2, kinahanglan ug tubig")
        assertEquals(Urgency.CRITICAL, result.urgency)
        assertEquals("1 trapped, need water, Purok 2", result.summary)
    }

    @Test
    fun bisayaFloodIsHelpNotCritical() {
        val result = TriageEngine.triage("Tabang, naa mi sa baha, kinahanglan ug pagkaon")
        assertEquals(Urgency.NEEDS_HELP, result.urgency)
        assertTrue(result.summary.contains("flood"))
        assertTrue(result.summary.contains("food"))
    }

    @Test
    fun englishInjuredNeedsMedicine() {
        val result = TriageEngine.triage("Two people injured and bleeding, need medicine")
        assertEquals(Urgency.CRITICAL, result.urgency)
        assertEquals("2 injured, need medicine", result.summary)
    }

    @Test
    fun englishSafeIgnoresNegatedInjury() {
        val result = TriageEngine.triage("We are safe, no injuries")
        assertEquals(Urgency.SAFE, result.urgency)
        assertEquals("reports safe", result.summary)
    }

    @Test
    fun tagalogSafeWithBarangay() {
        val result = TriageEngine.triage("Ligtas na kami dito sa Barangay San Roque")
        assertEquals(Urgency.SAFE, result.urgency)
        assertEquals("reports safe, Barangay San Roque", result.summary)
    }

    @Test
    fun negatedSafeWithFloodNeedsHelp() {
        val result = TriageEngine.triage("Hindi ligtas dito, baha na")
        assertEquals(Urgency.NEEDS_HELP, result.urgency)
        assertTrue(result.summary.contains("flood"))
    }

    @Test
    fun waterAtSitio() {
        val result = TriageEngine.triage("Need water at Sitio Maligaya")
        assertEquals(Urgency.NEEDS_HELP, result.urgency)
        assertEquals("need water, Sitio Maligaya", result.summary)
    }

    @Test
    fun whisperTagalogConfusionsStillProduceTheDemoSummary() {
        // Host whisper.cpp on a Tagalog TTS clip returned this, including "purokapat" and "tubing".
        val result = TriageEngine.triage("Tatlong tao ang naipit sa purokapat, kailangan ng tubing.")
        assertEquals(Urgency.CRITICAL, result.urgency)
        assertEquals("3 trapped, need water, Purok 4", result.summary)
    }

    @Test
    fun whisperBisayaRoughTranscriptStillFlagsTrapped() {
        val result = TriageEngine.triage("USA kabata ang napiit sa balay, tabang kinahang lenog tubing.")
        assertEquals(Urgency.CRITICAL, result.urgency)
        assertTrue(result.summary.startsWith("1 trapped"))
        assertTrue(result.summary.contains("water"))
    }

    @Test
    fun emptyIsNotActionable() {
        val result = TriageEngine.triage("   ")
        assertFalse(result.actionable)
    }
}
