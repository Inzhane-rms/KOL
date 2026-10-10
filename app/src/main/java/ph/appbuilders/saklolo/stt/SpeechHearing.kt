package ph.appbuilders.saklolo.stt

import java.util.Locale
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
            if (cleaned == TranscriptLimit.UNAVAILABLE) {
                return SpeechHearing(cleaned, cleaned, Urgency.SAFE)
            }
            val shown = SpeechPolish.apply(cleaned, names).ifBlank { cleaned }
            val fromRaw = TriageEngine.triage(cleaned)
            val fromShown = if (shown.equals(cleaned, ignoreCase = true)) fromRaw else TriageEngine.triage(shown)
            return SpeechHearing(cleaned, shown, higher(fromRaw, fromShown).urgency)
        }

        fun higher(left: TriageResult, right: TriageResult): TriageResult =
            if (left.urgency.rank <= right.urgency.rank) left else right
    }
}

/** Greedy only. Each clip gets at least 15 seconds, or four times its length. */
object DecodeBudget {
    const val SLOW_FACTOR = 1.5
    const val CLIP_FACTOR = 4.0
    const val MIN_DEADLINE_MS = 15_000L

    fun deadlineMs(clipSeconds: Double): Long {
        val scaled = (clipSeconds * CLIP_FACTOR * 1000.0).toLong()
        return maxOf(MIN_DEADLINE_MS, scaled)
    }

    fun allowBeam(configuredBeam: Int, earnedFast: Boolean): Boolean = configuredBeam > 1 && earnedFast

    fun markFast(elapsedSeconds: Double, clipSeconds: Double): Boolean =
        clipSeconds > 0.0 && elapsedSeconds < clipSeconds

    fun markSlow(elapsedSeconds: Double, clipSeconds: Double): Boolean =
        clipSeconds > 0.0 && elapsedSeconds > clipSeconds * SLOW_FACTOR
}

/** Greedy until a fast clip earns beam. A slow or aborted beam drops it again. */
object BeamSelect {
    fun nextBeam(configuredBeam: Int, earnedFast: Boolean): Int =
        if (DecodeBudget.allowBeam(configuredBeam, earnedFast)) configuredBeam else 1

    fun remember(wasEarned: Boolean, beam: Int, elapsedSeconds: Double, clipSeconds: Double, aborted: Boolean): Boolean {
        if (beam == 1 && !aborted && DecodeBudget.markFast(elapsedSeconds, clipSeconds)) return true
        if (beam > 1 && (aborted || DecodeBudget.markSlow(elapsedSeconds, clipSeconds))) return false
        return wasEarned
    }
}

object TranscriptLimit {
    const val UNAVAILABLE = "transcript unavailable"
}

/** Gemma may clean wording only when the rules did not flag an emergency. */
object CaptionCleanup {
    fun allowModel(urgency: Urgency, raw: String, shown: String): Boolean {
        if (urgency == Urgency.CRITICAL) return false
        if (TriageEngine.mentionsEmergency(raw) || TriageEngine.mentionsEmergency(shown)) return false
        return shown.isNotBlank()
    }

    /** A cleanup that invents an emergency is discarded. */
    fun acceptModel(before: String, after: String): Boolean {
        val next = after.trim()
        if (next.isEmpty()) return false
        if (TriageEngine.mentionsEmergency(next) && !TriageEngine.mentionsEmergency(before)) return false
        val beforeUrgency = TriageEngine.triage(before).urgency
        val afterUrgency = TriageEngine.triage(next).urgency
        return afterUrgency != Urgency.CRITICAL || beforeUrgency == Urgency.CRITICAL
    }
}

object SpeechPolish {
    fun apply(raw: String, names: List<String> = emptyList()): String {
        val corrected = SpeechLexicon.correct(raw, names)
        val gated = GateNumbers.apply(corrected)
        return SentenceCase.apply(gated)
    }
}

object Hallucination {
    private val wrappers = listOf(
        "[music]", "[applause]", "[laughter]", "[blank_audio]", "[noise]",
        "(music)", "(applause)",
    )
    private val lines = listOf(
        "thank you",
        "thank you.",
        "thanks for watching",
        "thanks for watching.",
        "thanks for listening",
        "thanks for listening.",
    )

    fun strip(raw: String): String {
        var text = raw
        for (token in wrappers) {
            text = text.replace(token, " ", ignoreCase = true)
        }
        for (phrase in listOf("thanks for watching", "thanks for listening", "thank you")) {
            text = text.replace(Regex("(?i)\\b${Regex.escape(phrase)}\\b[.!?]*"), " ")
        }
        val kept = text.split(Regex("[\\n.]+"))
            .map { it.trim() }
            .filter { sentence ->
                val key = sentence.lowercase(Locale.US).trim()
                key.isNotEmpty() && lines.none { key == it || key == it.trimEnd('.') }
            }
        val words = kept.joinToString(" ").split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        val collapsed = mutableListOf<String>()
        var run = 0
        var previous = ""
        for (word in words) {
            if (word.equals(previous, ignoreCase = true)) {
                run += 1
                if (run >= 2) continue
            } else {
                run = 1
                previous = word
            }
            collapsed += word
        }
        return collapsed.joinToString(" ")
    }
}

object GateNumbers {
    private val words = mapOf(
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10",
        "isa" to "1", "isang" to "1",
        "dalawa" to "2", "dalawang" to "2",
        "tatlo" to "3", "tatlong" to "3", "tres" to "3",
        "apat" to "4",
        "lima" to "5", "limang" to "5",
        "anim" to "6",
        "pito" to "7", "pitong" to "7",
        "walo" to "8", "walong" to "8",
        "siyam" to "9",
        "sampu" to "10", "sampung" to "10",
    )

    fun apply(raw: String): String {
        val pattern = Regex("(?i)\\bgate\\s+([a-z]+|\\d+)\\b")
        return pattern.replace(raw) { match ->
            val token = match.groupValues[1].lowercase(Locale.US)
            val digit = token.toIntOrNull()?.toString() ?: words[token]
            if (digit == null) match.value else "Gate $digit"
        }
    }
}

object SentenceCase {
    fun apply(raw: String): String {
        val trimmed = raw.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return ""
        val first = trimmed.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
        }
        return first
    }
}

/**
 * Fixes likely Whisper slips against emergency words, Taglish chat words, and
 * saved names. An emergency word is never rewritten into a different word.
 * A non-emergency word becomes an emergency word only when the edit distance is 1.
 */
object SpeechLexicon {
    val EMERGENCY = setOf(
        "nahimatay", "hinimatay", "himalatyon",
        "naipit", "naiipit", "napiit", "nakulong", "nakakulong", "nadaganan",
        "nasugatan", "sugatan", "dumudugo", "nagdudugo", "unconscious",
        "nasusunog", "nagdilaab", "nalulunod", "nalunod",
        "namamatay", "namatay", "ambulansya",
        "makahinga", "humihinga", "makalabas", "makaginhawa",
    )

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
        "gate", "stage", "entrance", "exit", "barricade", "saan", "kita", "tayo",
        "first", "aid", "cr", "vip",
    )

    fun correct(raw: String, names: List<String> = emptyList()): String {
        val stripped = Hallucination.strip(raw)
        val entries = entries(names)
        if (stripped.isBlank() || entries.isEmpty()) return stripped.trim()
        return stripped.trim().split(Regex("\\s+")).joinToString(" ") { token ->
            val (lead, core, tail) = edges(token)
            if (core.isEmpty()) return@joinToString token
            val key = normalize(core)
            if (key in EMERGENCY) return@joinToString lead + displayOf(key) + tail
            val replacement = match(key, entries) ?: return@joinToString token
            lead + replacement + tail
        }
    }

    fun entries(names: List<String>): List<Pair<String, String>> {
        val map = linkedMapOf<String, String>()
        for (word in WORDS) {
            val key = normalize(word)
            if (key.isNotEmpty()) map.putIfAbsent(key, displayOf(key))
        }
        for (name in names) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) continue
            for (part in trimmed.split(Regex("\\s+"))) {
                val key = normalize(part)
                if (key.length >= 4 && key !in EMERGENCY) map[key] = part
            }
        }
        return map.toList()
    }

    fun normalize(token: String): String =
        token.lowercase(Locale.US).replace(Regex("[^\\p{L}\\p{N}]"), "")

    /** o/u and e/i fold, and ng stays one unit so it is not split. */
    fun phonetic(token: String): String {
        val folded = normalize(token).replace("ng", "ŋ")
        return buildString(folded.length) {
            for (char in folded) {
                append(
                    when (char) {
                        'o' -> 'u'
                        'e' -> 'i'
                        else -> char
                    },
                )
            }
        }
    }

    internal fun match(token: String, entries: List<Pair<String, String>>): String? {
        if (token.isEmpty() || token in EMERGENCY) return displayOf(token).takeIf { token in EMERGENCY }
        val limit = maxDistance(token.length)
        if (limit == 0 && entries.none { it.first == token }) return null
        var best = limit + 1
        var display: String? = null
        var tied = false
        for ((key, shown) in entries) {
            if (key.firstOrNull() != token.firstOrNull()) continue
            val score = score(token, key, limit)
            if (score > limit) continue
            if (key in EMERGENCY && !veryClose(token, key)) continue
            if (score < best) {
                best = score
                display = shown
                tied = false
            } else if (score == best && shown != display) {
                tied = true
            }
        }
        if (tied || best == 0 && display == null) return null
        if (tied) return null
        if (best == 0) return display
        return display
    }

    fun maxDistance(length: Int): Int = (length / 4).coerceIn(0, 3)

    /** Promoting a token into an emergency word needs a single edit and a similar length. */
    fun veryClose(token: String, emergency: String): Boolean {
        if (abs(token.length - emergency.length) > 1) return false
        return damerau(token, emergency, 1) <= 1
    }

    private fun score(token: String, key: String, limit: Int): Int {
        val raw = damerau(token, key, limit)
        if (key in EMERGENCY) return raw
        val spoken = damerau(phonetic(token), phonetic(key), limit)
        return minOf(raw, spoken)
    }

    private fun displayOf(key: String): String = when (key) {
        "cr" -> "CR"
        "vip" -> "VIP"
        else -> key
    }

    /** Optimal string alignment (Damerau-Levenshtein with adjacent transpositions). */
    fun damerau(left: String, right: String, limit: Int): Int {
        if (abs(left.length - right.length) > limit) return limit + 1
        if (left == right) return 0
        val rows = left.length + 1
        val cols = right.length + 1
        var prev2 = IntArray(cols)
        var prev = IntArray(cols) { it }
        var curr = IntArray(cols)
        for (i in 1 until rows) {
            curr[0] = i
            var rowBest = curr[0]
            for (j in 1 until cols) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                var value = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && left[i - 1] == right[j - 2] && left[i - 2] == right[j - 1]) {
                    value = minOf(value, prev2[j - 2] + 1)
                }
                curr[j] = value
                if (value < rowBest) rowBest = value
            }
            if (rowBest > limit) return limit + 1
            val swap = prev2
            prev2 = prev
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

object SpeechScore {
    data class Report(val exact: Int, val total: Int, val wordHits: Int, val wordRefs: Int) {
        val exactRate: Double get() = if (total == 0) 0.0 else exact.toDouble() / total
        val wordRate: Double get() = if (wordRefs == 0) 0.0 else wordHits.toDouble() / wordRefs
    }

    fun report(pairs: List<Pair<String, String>>, transform: (String) -> String): Report {
        var exact = 0
        var hits = 0
        var refs = 0
        for ((noisy, expected) in pairs) {
            val got = normalize(transform(noisy))
            val want = normalize(expected)
            if (got == want) exact += 1
            val gotWords = got.split(" ").filter { it.isNotEmpty() }
            val wantWords = want.split(" ").filter { it.isNotEmpty() }
            refs += wantWords.size
            val bag = gotWords.toMutableList()
            for (word in wantWords) {
                if (bag.remove(word)) hits += 1
            }
        }
        return Report(exact, pairs.size, hits, refs)
    }

    private fun normalize(text: String): String =
        text.lowercase(Locale.US).replace(Regex("[^\\p{L}\\p{N}\\s]"), "").replace(Regex("\\s+"), " ").trim()
}
