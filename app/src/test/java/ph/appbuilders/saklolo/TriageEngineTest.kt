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

    @Test
    fun naiipitIsCritical() {
        val result = TriageEngine.triage("May naiipit sa loob")
        assertEquals(Urgency.CRITICAL, result.urgency)
        assertTrue(result.summary.contains("trapped"))
    }

    @Test
    fun naipitIsCritical() {
        val result = TriageEngine.triage("Naipit ang kamay ko")
        assertEquals(Urgency.CRITICAL, result.urgency)
        assertTrue(result.summary.contains("trapped"))
    }

    @Test
    fun bareIpitIsNotCritical() {
        val hair = TriageEngine.triage("ipit sa buhok")
        val door = TriageEngine.triage("Ipit ang pinto")
        assertEquals(Urgency.NEEDS_HELP, hair.urgency)
        assertEquals(Urgency.NEEDS_HELP, door.urgency)
    }

    @Test
    fun ipitNaIpitAloneNeedsHelp() {
        val result = TriageEngine.triage("ipit na ipit dito")
        assertEquals(Urgency.NEEDS_HELP, result.urgency)
    }

    @Test
    fun ipitNaIpitWithHindiMakahingaIsCritical() {
        val result = TriageEngine.triage("ipit na ipit, hindi makahinga")
        assertEquals(Urgency.CRITICAL, result.urgency)
    }

    @Test
    fun ipitNaIpitWithNahimatayIsCritical() {
        assertEquals(Urgency.CRITICAL, TriageEngine.triage("ipit na ipit at nahimatay").urgency)
    }

    @Test
    fun siksikanAloneIsNotCritical() {
        assertEquals(Urgency.NEEDS_HELP, TriageEngine.triage("ang siksikan dito!").urgency)
        assertEquals(Urgency.NEEDS_HELP, TriageEngine.triage("nagsisiksikan").urgency)
        assertEquals(Urgency.NEEDS_HELP, TriageEngine.triage("dinumog kami").urgency)
        assertEquals(Urgency.NEEDS_HELP, TriageEngine.triage("nahilo ako").urgency)
    }

    @Test
    fun crushAloneIsNotCritical() {
        assertEquals(Urgency.NEEDS_HELP, TriageEngine.triage("my crush is here").urgency)
        assertEquals(Urgency.CRITICAL, TriageEngine.triage("crowd crush sa harap").urgency)
    }

    @Test
    fun patayAloneIsNotCriticalButPatayNaAndNamatayAre() {
        assertEquals(Urgency.NEEDS_HELP, TriageEngine.triage("patay").urgency)
        assertEquals(Urgency.NEEDS_HELP, TriageEngine.triage("may patay").urgency)
        assertEquals(Urgency.CRITICAL, TriageEngine.triage("patay na").urgency)
        assertEquals(Urgency.CRITICAL, TriageEngine.triage("patay na siya").urgency)
        assertEquals(Urgency.CRITICAL, TriageEngine.triage("namatay si Ana").urgency)
        assertEquals(Urgency.CRITICAL, TriageEngine.triage("namamatay siya").urgency)
    }

    @Test
    fun nadagananAndHinimatayAreCritical() {
        assertEquals(Urgency.CRITICAL, TriageEngine.triage("nadaganan siya").urgency)
        assertEquals(Urgency.CRITICAL, TriageEngine.triage("hinimatay si Ana").urgency)
        assertEquals(Urgency.CRITICAL, TriageEngine.triage("ang siksikan at nahimatay").urgency)
    }

    @Test
    fun nagdudugoIsCritical() {
        val result = TriageEngine.triage("Nagdudugo ang kamay")
        assertEquals(Urgency.CRITICAL, result.urgency)
        assertTrue(result.summary.contains("injured"))
    }

    @Test
    fun dumugoIsCritical() {
        val result = TriageEngine.triage("Dumugo ang ulo")
        assertEquals(Urgency.CRITICAL, result.urgency)
        assertTrue(result.summary.contains("injured"))
    }

    @Test
    fun hindiWithOneWordBeforeHumihingaIsCritical() {
        val na = TriageEngine.triage("Hindi na humihinga")
        val siya = TriageEngine.triage("Hindi siya humihinga")
        assertEquals(Urgency.CRITICAL, na.urgency)
        assertEquals(Urgency.CRITICAL, siya.urgency)
        assertTrue(na.summary.contains("medical"))
        assertTrue(siya.summary.contains("medical"))
    }

    @Test
    fun hindiWithTwoWordsBeforeHumihingaIsCritical() {
        val result = TriageEngine.triage("Hindi po siya humihinga")
        assertEquals(Urgency.CRITICAL, result.urgency)
    }

    @Test
    fun diWithWordsBeforeHumihingaIsCritical() {
        val one = TriageEngine.triage("Di na humihinga")
        val two = TriageEngine.triage("Di na po humihinga")
        assertEquals(Urgency.CRITICAL, one.urgency)
        assertEquals(Urgency.CRITICAL, two.urgency)
    }

    @Test
    fun exactHindiHumihingaStaysCritical() {
        assertEquals(Urgency.CRITICAL, TriageEngine.triage("Hindi humihinga").urgency)
        assertEquals(Urgency.CRITICAL, TriageEngine.triage("Di humihinga").urgency)
    }

    @Test
    fun threeWordsBetweenHindiAndHumihingaIsNotCritical() {
        val result = TriageEngine.triage("Hindi na talaga siya humihinga")
        assertEquals(Urgency.NEEDS_HELP, result.urgency)
    }
}
