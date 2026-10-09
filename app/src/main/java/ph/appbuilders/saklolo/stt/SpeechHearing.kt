package ph.appbuilders.saklolo.stt

import kotlin.math.abs
import ph.appbuilders.saklolo.triage.TriageEngine
import ph.appbuilders.saklolo.triage.TriageResult
import ph.appbuilders.saklolo.triage.Urgency

/**
 * Whisper text after the decode. The screen shows [shown]. [raw] is what the
 * model returned. Urgency is the higher of the two triage results.
 */
data class SpeechHearing(
    val raw: String,
    val shown: String,
    val urgency: Urgency,
) {
    val emergency: Boolean get() = urgency == Urgency.CRITICAL

    companion object {
        fun interpret(raw: String, names: List<String> = emptyList()): SpeechHearing {
            val cleaned = raw.trim()
            val shown = SpeechLexicon.correct(cleaned, names).ifBlank { cleaned }
            val fromRaw = TriageEngine.triage(cleaned)
            val fromShown = if (shown == cleaned) fromRaw else TriageEngine.triage(shown)
            return SpeechHearing(cleaned, shown, higher(fromRaw, fromShown).urgency)
        }

        fun higher(left: TriageResult, right: TriageResult): TriageResult =
            if (left.urgency.rank <= right.urgency.rank) left else right
    }
}

/**
 * Fixes likely Whisper slips against emergency words, Taglish chat words, and
 * saved names. Only tokens of 5 or more characters are candidates.
 */
object SpeechLexicon {
    val WORDS = listOf(
        "nahimatay", "hinimatay",
        "naipit", "naiipit", "nadaganan", "nakulong", "nakakulong",
        "makahinga", "humihinga", "makalabas", "makaginhawa",
        "hindi", "nasugatan", "sugatan", "dumudugo", "nagdudugo", "unconscious", "himalatyon",
        "namamatay", "namatay", "ambulansya", "ambulance", "landslide",
        "nalulunod", "nalunod", "drowning",
        "siksikan", "nagsisiksikan", "nasusunog", "nagdilaab",
        "bumabaha", "flooding",
        "tulong", "saklolo", "kinahanglan", "tubig", "pagkain", "pagkaon",
        "gamot", "tambal", "medicine", "stranded", "evacuate", "evacuation", "lumikas",
        "shelter", "brownout", "kuryente", "nawawala", "missing", "masakit",
        "nasaan", "nandito", "tawagan", "medic", "barkada", "papunta", "sandali",
    )

    fun correct(raw: String, names: List<String> = emptyList()): String {
        val entries = entries(names)
        if (raw.isBlank() || entries.isEmpty()) return raw.trim()
        return raw.trim().split(Regex("\\s+")).joinToString(" ") { token ->
            val (lead, core, tail) = edges(token)
            if (core.isEmpty()) return@joinToString token
            val key = normalize(core)
            if (key.length < 5) return@joinToString token
            val replacement = match(key, entries) ?: return@joinToString token
            lead + replacement + tail
        }
    }

    fun entries(names: List<String>): List<Pair<String, String>> {
        val map = linkedMapOf<String, String>()
        for (word in WORDS) {
            val key = normalize(word)
            if (key.length >= 5) map.putIfAbsent(key, key)
        }
        for (name in names) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) continue
            for (part in trimmed.split(Regex("\\s+"))) {
                val key = normalize(part)
                if (key.length >= 5) map[key] = part
            }
        }
        return map.toList()
    }

    /** Lowercase, punctuation removed. */
    fun normalize(token: String): String =
        token.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]"), "")

    internal fun match(token: String, entries: List<Pair<String, String>>): String? {
        if (token.length < 5) return null
        val limit = if (token.length >= 8) 2 else 1
        var best = limit + 1
        var display: String? = null
        var tied = false
        for ((key, shown) in entries) {
            val score = distance(token, key, limit)
            if (score > limit) continue
            if (score < best) {
                best = score
                display = shown
                tied = false
            } else if (score == best && shown != display) {
                tied = true
            }
        }
        if (tied) return null
        return display
    }

    /** Levenshtein, capped. A result above [limit] means "too far". */
    fun distance(left: String, right: String, limit: Int): Int {
        if (abs(left.length - right.length) > limit) return limit + 1
        if (left == right) return 0
        var prev = IntArray(right.length + 1) { it }
        var curr = IntArray(right.length + 1)
        for (i in 1..left.length) {
            curr[0] = i
            var rowBest = curr[0]
            for (j in 1..right.length) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                if (curr[j] < rowBest) rowBest = curr[j]
            }
            if (rowBest > limit) return limit + 1
            val swap = prev
            prev = curr
            curr = swap
        }
        return prev[right.length]
    }

    private fun edges(token: String): Triple<String, String, String> {
        var start = 0
        var end = token.length
        while (start < end && !token[start].isLetterOrDigit()) start++
        while (end > start && !token[end - 1].isLetterOrDigit()) end--
        return Triple(token.substring(0, start), token.substring(start, end), token.substring(end))
    }
}
