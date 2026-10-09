package ph.appbuilders.saklolo.ask

import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Offline safety Q&A. Answers are copied from the bundled bank.
 * Nothing in this object writes a new sentence.
 *
 * Medical phrases are checked first, as whole words. Otherwise a pair matches
 * only when at least [MIN_OVERLAP] content words overlap and those words cover
 * at least [MIN_COVERAGE] of the question. Below that, the stored fallback is used.
 */
object AskEngine {
    const val MIN_OVERLAP = 2
    const val MIN_COVERAGE = 0.75

    private val json = Json { ignoreUnknownKeys = true }

    private val stopwords = setOf(
        "a", "an", "the", "i", "im", "you", "my", "me", "do", "to", "of", "in", "on", "if",
        "is", "it", "can", "what", "when", "where", "how", "should", "for", "and", "or",
        "there", "s", "am", "be", "your", "our",
        "ang", "ng", "na", "sa", "mga", "ba", "ko", "mo", "ako", "ka", "at", "o", "kung",
        "para", "ito", "yan", "yun", "ay", "po", "bang", "nga", "din", "rin", "lang",
        "muna", "naman", "si", "ni", "kay", "may", "mayroon", "meron",
    )

    fun parse(raw: String): SafetyBank {
        val file = json.decodeFromString(BankFile.serializer(), raw)
        return SafetyBank(
            sources = file.sources,
            triggers = file.medicalOverride.triggers,
            medicalTl = file.medicalOverride.answerTl,
            medicalEn = file.medicalOverride.answerEn,
            pairs = file.pairs.map {
                QaPair(it.id, it.questionTl, it.questionEn, it.answerTl, it.answerEn, it.src)
            },
            fallbackTl = file.noMatch.answerTl,
            fallbackEn = file.noMatch.answerEn,
            disclaimerTl = file.disclaimerTl,
            disclaimerEn = file.disclaimerEn,
        )
    }

    fun tokens(raw: String): List<String> =
        normalize(raw).split(' ').filter { it.isNotEmpty() }

    fun normalize(raw: String): String {
        val lower = raw.lowercase(Locale.US)
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val withoutMarks = decomposed.replace(Regex("\\p{Mn}+"), "")
        val glued = withoutMarks.replace(Regex("['’]"), "")
        return glued.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    fun answer(question: String, bank: SafetyBank): AskResult {
        val words = tokens(question)
        if (words.isEmpty()) return fallback(bank)
        if (isMedical(words, bank)) {
            return AskResult.Emergency(bank.medicalTl, bank.medicalEn)
        }
        val content = words.filter { it !in stopwords }
        if (content.isEmpty()) return fallback(bank)
        val best = bank.pairs
            .map { pair -> pair to score(content, pair) }
            .filter { (_, scored) -> scored.overlap >= MIN_OVERLAP && scored.coverage >= MIN_COVERAGE }
            .maxWithOrNull(compareBy<Pair<QaPair, Score>> { it.second.coverage }
                .thenBy { it.second.overlap }
                .thenByDescending { it.first.id })
            ?.first
        return if (best == null) fallback(bank) else tipFor(best)
    }

    /** Gemma may return only a pair id or NONE. Any other text is ignored. */
    fun parsePairChoice(raw: String, validIds: Set<Int>): Int? {
        val text = raw.trim()
        if (text.equals("NONE", ignoreCase = true)) return null
        val id = text.toIntOrNull() ?: return null
        return id.takeIf { it in validIds }
    }

    fun tipFor(pair: QaPair): AskResult.Tip = AskResult.Tip(
        askedLabel = pair.questionTl,
        answerTl = pair.answerTl,
        answerEn = pair.answerEn,
        sourceCode = pair.sourceCode,
        pairId = pair.id,
    )

    fun suggestions(bank: SafetyBank): List<AskSuggestion> {
        val labels = mapOf(
            4 to "Kailan lilikas?",
            12 to "Go-bag",
            8 to "Kable ng kuryente",
        )
        return labels.map { (id, label) ->
            val pair = bank.pairs.first { it.id == id }
            AskSuggestion(label = label, question = pair.questionTl)
        }
    }

    private fun fallback(bank: SafetyBank) = AskResult.Fallback(bank.fallbackTl, bank.fallbackEn)

    private fun isMedical(words: List<String>, bank: SafetyBank): Boolean =
        bank.triggers.any { trigger ->
            val phrase = tokens(trigger)
            phrase.isNotEmpty() && containsPhrase(words, phrase)
        }

    private fun containsPhrase(words: List<String>, phrase: List<String>): Boolean {
        if (phrase.size > words.size) return false
        for (start in 0..words.size - phrase.size) {
            if (words.subList(start, start + phrase.size) == phrase) return true
        }
        return false
    }

    private fun score(query: List<String>, pair: QaPair): Score {
        val pairWords = tokens(pair.questionTl + " " + pair.questionEn)
            .filter { it !in stopwords }
            .toSet()
        val overlap = query.count { it in pairWords }
        return Score(overlap, overlap.toDouble() / query.size)
    }

    private data class Score(val overlap: Int, val coverage: Double)
}

data class SafetyBank(
    val sources: Map<String, String>,
    val triggers: List<String>,
    val medicalTl: String,
    val medicalEn: String,
    val pairs: List<QaPair>,
    val fallbackTl: String,
    val fallbackEn: String,
    val disclaimerTl: String,
    val disclaimerEn: String,
) {
    fun sourceLabel(code: String): String =
        code.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ") { part ->
            when (part) {
                "H" -> "Gov't Disaster Preparedness Handbook"
                "U" -> "UNICEF Philippines"
                else -> sources[part] ?: part
            }
        }
}

data class QaPair(
    val id: Int,
    val questionTl: String,
    val questionEn: String,
    val answerTl: String,
    val answerEn: String,
    val sourceCode: String,
)

data class AskSuggestion(val label: String, val question: String)

sealed class AskResult {
    data class Emergency(val answerTl: String, val answerEn: String) : AskResult()
    data class Tip(
        val askedLabel: String,
        val answerTl: String,
        val answerEn: String,
        val sourceCode: String,
        val pairId: Int,
    ) : AskResult()
    data class Fallback(val answerTl: String, val answerEn: String) : AskResult()
}

@Serializable
private data class BankFile(
    val sources: Map<String, String> = emptyMap(),
    @SerialName("medical_override") val medicalOverride: MedicalFile,
    val pairs: List<PairFile>,
    @SerialName("no_match") val noMatch: NoMatchFile,
    @SerialName("disclaimer_en") val disclaimerEn: String,
    @SerialName("disclaimer_tl") val disclaimerTl: String,
)

@Serializable
private data class MedicalFile(
    val triggers: List<String>,
    @SerialName("answer_tl") val answerTl: String,
    @SerialName("answer_en") val answerEn: String,
)

@Serializable
private data class PairFile(
    val id: Int,
    @SerialName("q_tl") val questionTl: String,
    @SerialName("q_en") val questionEn: String,
    @SerialName("a_tl") val answerTl: String,
    @SerialName("a_en") val answerEn: String,
    val src: String,
)

@Serializable
private data class NoMatchFile(
    @SerialName("a_tl") val answerTl: String,
    @SerialName("a_en") val answerEn: String,
)
