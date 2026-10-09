package ph.appbuilders.saklolo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.ask.AskEngine
import ph.appbuilders.saklolo.ask.AskResult
import ph.appbuilders.saklolo.ask.SafetyBank

class AskEngineTest {
    private val bank: SafetyBank = AskEngine.parse(bundledJson())

    @Test
    fun bankHasEighteenPairsAndThirtyNineTriggers() {
        assertEquals(18, bank.pairs.size)
        assertEquals(39, bank.triggers.size)
        val pair = bank.pairs.first { it.id == 3 }
        assertEquals("Manatili sa loob kung ligtas. Kung binabaha, lumikas sa evacuation center.", pair.answerTl)
        assertEquals("H,U", pair.sourceCode)
    }

    @Test
    fun normalizeDropsCaseAccentsAndPunctuation() {
        assertEquals(listOf("pwede", "ba", "tumawid", "sa", "baha"), AskEngine.tokens("Pwéde ba tumawid sa baha?"))
        assertEquals(listOf("cant", "breathe"), AskEngine.tokens("Can't breathe!"))
        assertEquals(listOf("go", "bag"), AskEngine.tokens("Go-bag"))
    }

    @Test
    fun wholeWordsDoNotMatchSubstrings() {
        assertFalse(AskEngine.tokens("babalik").contains("bali"))
        assertFalse(AskEngine.tokens("kasugatan").contains("sugat"))
        assertFalse(AskEngine.tokens("bahain").contains("baha"))
        assertTrue(AskEngine.answer("babalik ako sa bahay", bank) is AskResult.Tip)
        assertTrue(AskEngine.answer("may kasugatan sa tuhod", bank) is AskResult.Fallback)
    }

    @Test
    fun floodCrossingUsesTheStoredNoCrossingAnswer() {
        val result = AskEngine.answer("pwede ba tumawid sa baha?", bank) as AskResult.Tip
        val pair = bank.pairs.first { it.id == 1 }
        assertEquals(pair.questionTl, result.askedLabel)
        assertEquals(pair.answerTl, result.answerTl)
        assertEquals(pair.answerEn, result.answerEn)
        assertEquals("Huwag. Huwag tumawid sa umaagos na tubig o bahang lampas tuhod.", result.answerTl)
        assertEquals(1, result.pairId)
    }

    @Test
    fun goingHomeWaitsForTheLgu() {
        val result = AskEngine.answer("babalik ako sa bahay", bank) as AskResult.Tip
        assertEquals(15, result.pairId)
        assertEquals("Huwag muna. Bumalik lang kapag sinabi ng LGU na ligtas na.", result.answerTl)
        assertEquals("Not yet. Return only when your LGU says it's safe.", result.answerEn)
    }

    @Test
    fun bleedingIsAMedicalOverrideWithNoTip() {
        val result = AskEngine.answer("may sugat at dumudugo", bank)
        assertTrue(result is AskResult.Emergency)
        val emergency = result as AskResult.Emergency
        assertEquals(bank.medicalTl, emergency.answerTl)
        assertEquals("Emergency ito. Mag-SOS ngayon.", emergency.answerTl)
        assertEquals("This is an emergency. Send an SOS now.", emergency.answerEn)
        assertFalse(emergency.answerTl.contains("tuhod"))
    }

    @Test
    fun bareBaliIsNotMedicalButBleedingAndNotBreathingAre() {
        val bali = AskEngine.answer("Bali, kailan lilikas?", bank)
        assertFalse(bali is AskResult.Emergency)
        assertTrue(AskEngine.answer("nagdudugo ang kamay", bank) is AskResult.Emergency)
        assertTrue(AskEngine.answer("hindi na humihinga", bank) is AskResult.Emergency)
        assertTrue(AskEngine.answer("bali ang buto", bank) is AskResult.Emergency)
    }

    @Test
    fun keywordTipsAreNotMarkedAsModelPicks() {
        val tip = AskEngine.answer("pwede ba tumawid sa baha?", bank) as AskResult.Tip
        assertFalse(tip.fromModel)
        assertFalse(AskEngine.tipFor(bank.pairs.first()).fromModel)
    }

    @Test
    fun recorderTopicsUseStoredBankText() {
        val topics = AskEngine.recorderTopics(bank)
        assertEquals(listOf("Flood at home", "Someone hurt", "When to evacuate"), topics.map { it.label })
        assertEquals(bank.pairs.first { it.id == 3 }.questionTl, topics[0].question)
        assertTrue(bank.triggers.contains(topics[1].question))
        assertTrue(AskEngine.answer(topics[1].question, bank) is AskResult.Emergency)
        assertEquals(bank.pairs.first { it.id == 4 }.questionTl, topics[2].question)
    }

    @Test
    fun medicalPhrasesAreWholeWords() {
        assertTrue(AskEngine.answer("the patient is not breathing", bank) is AskResult.Emergency)
        assertTrue(AskEngine.answer("heart attack", bank) is AskResult.Emergency)
        assertTrue(AskEngine.answer("just breathing", bank) is AskResult.Fallback)
        assertTrue(AskEngine.answer("heart", bank) is AskResult.Fallback)
    }

    @Test
    fun gasLeakFallsBack() {
        val result = AskEngine.answer("May amoy ng gas leak sa kusina.", bank) as AskResult.Fallback
        assertEquals("Wala akong sagot dito. Sumunod sa LGU o barangay.", result.answerTl)
        assertEquals(bank.fallbackEn, result.answerEn)
    }

    @Test
    fun unrelatedQuestionFallsBack() {
        val result = AskEngine.answer("Magkano ang bigas sa palengke?", bank) as AskResult.Fallback
        assertEquals(bank.fallbackTl, result.answerTl)
        assertEquals("I don't have an answer for this. Follow your LGU or barangay.", result.answerEn)
    }

    @Test
    fun pairChoiceAcceptsOnlyAnIdOrNone() {
        val ids = bank.pairs.map { it.id }.toSet()
        assertEquals(4, AskEngine.parsePairChoice("4", ids))
        assertEquals(12, AskEngine.parsePairChoice(" 12 ", ids))
        assertNull(AskEngine.parsePairChoice("NONE", ids))
        assertNull(AskEngine.parsePairChoice("none", ids))
        assertNull(AskEngine.parsePairChoice("I think 4", ids))
        assertNull(AskEngine.parsePairChoice("11", ids))
        assertNull(AskEngine.parsePairChoice("4\nextra", ids))
    }

    @Test
    fun suggestionsComeFromTheBankAndAreNotGas() {
        val suggestions = AskEngine.suggestions(bank)
        assertEquals(listOf(4, 12, 8), suggestions.map { bank.pairs.first { pair -> pair.questionTl == it.question }.id })
        suggestions.forEach { suggestion ->
            assertFalse(AskEngine.tokens(suggestion.question).contains("gas"))
            val answered = AskEngine.answer(suggestion.question, bank) as AskResult.Tip
            assertEquals(suggestion.question, answered.askedLabel)
        }
    }

    private fun bundledJson(): String {
        val file = listOf(
            File("src/main/assets/ask/ask_blink_qa.json"),
            File("app/src/main/assets/ask/ask_blink_qa.json"),
        ).first { it.exists() }
        return file.readText()
    }
}
