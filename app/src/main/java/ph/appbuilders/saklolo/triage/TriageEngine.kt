package ph.appbuilders.saklolo.triage

import java.util.Locale

enum class Urgency(val rank: Int, val label: String) {
    CRITICAL(0, "CRITICAL"),
    NEEDS_HELP(1, "NEEDS HELP"),
    SAFE(2, "SAFE"),
}

data class TriageResult(
    val urgency: Urgency,
    val summary: String,
    val actionable: Boolean,
)

/**
 * Offline urgency triage for Tagalog, Bisaya, and English disaster speech.
 * This is a keyword and phrase classifier. It does not call a network and it
 * does not load a language model. Summaries are short English alerts so a
 * responder can scan them, for example "3 trapped, need water, Purok 4".
 */
object TriageEngine {
    private val numberWords = mapOf(
        "0" to 0, "1" to 1, "2" to 2, "3" to 3, "4" to 4, "5" to 5,
        "6" to 6, "7" to 7, "8" to 8, "9" to 9, "10" to 10,
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "isa" to 1, "isang" to 1,
        "dalawa" to 2, "dalawang" to 2,
        "tatlo" to 3, "tatlong" to 3,
        "apat" to 4,
        "lima" to 5, "limang" to 5,
        "anim" to 6,
        "pito" to 7, "pitong" to 7,
        "walo" to 8, "walong" to 8,
        "siyam" to 9,
        "sampu" to 10, "sampung" to 10,
        "usa" to 1,
        "duha" to 2,
        "tulo" to 3,
        "upat" to 4,
        "unom" to 6,
        "napulo" to 10,
    )

    private val peopleWords = setOf(
        "tao", "tawo", "katawhan", "people", "persons", "person", "pamilya", "family",
        "bata", "kabata", "kabataan", "child", "children", "sanggol", "baby", "kauban", "residents", "matanda",
    )

    private val trappedWords = setOf(
        "naipit", "naiipit", "ipit", "napiit", "nakulong", "nakakulong", "trapped", "stuck", "buried", "pinned",
    )

    private val injuredWords = setOf(
        "nasugatan", "sugatan", "sugat", "nasamdan", "samdan", "injured", "bleeding",
        "dumudugo", "nagdudugo", "dumugo", "nagdugo", "unconscious", "nahimatay", "himalatyon", "injury", "injuries",
    )

    private val fireWords = setOf("sunog", "nasusunog", "nagdilaab", "fire", "apoy")
    private val drownWords = setOf("nalulunod", "nalunod", "nalumos", "drowning")
    private val severeWords = setOf(
        "namamatay", "namatay", "patay", "dead", "dying", "ambulansya", "ambulance", "dugo", "blood",
        "landslide", "gumuho",
    )

    private val floodWords = setOf("baha", "bumabaha", "flood", "flooding")

    private val helpWords = setOf(
        "tulong", "saklolo", "tabang", "help", "rescue", "kailangan", "kinahanglan",
        "tubig", "water", "pagkain", "pagkaon", "food", "gutom", "hungry", "gigutom",
        "gamot", "tambal", "medicine", "stranded", "evacuate", "evacuation", "lumikas",
        "likas", "shelter", "brownout", "kuryente", "nawawala", "missing", "sakit", "masakit",
    )

    private val safeWords = setOf("ligtas", "luwas", "safe", "okay", "ok", "ayos", "fine")
    private val negations = setOf("hindi", "huwag", "dili", "di", "not", "no", "wala", "walang")
    private val placeStop = setOf(
        "ang", "sa", "na", "ng", "ug", "the", "at", "of", "in", "near", "dito", "kami",
        "lang", "po", "area", "and", "with", "mi", "sila",
    )
    private val placeNumberPrev = setOf("purok", "zone", "barangay", "brgy", "sitio", "no", "number")

    private val trappedPhrases = listOf(
        listOf("hindi", "makalabas"),
        listOf("di", "makalabas"),
        listOf("dili", "makagawas"),
        listOf("cant", "get", "out"),
        listOf("cannot", "get", "out"),
        listOf("can", "t", "get", "out"),
    )
    private val severePhrases = listOf(
        listOf("not", "breathing"),
        listOf("hindi", "humihinga"),
        listOf("di", "humihinga"),
        listOf("hindi", "makahinga"),
        listOf("di", "makahinga"),
        listOf("dili", "makaginhawa"),
        listOf("wala", "nay", "ginhawa"),
        listOf("walang", "malay"),
        listOf("heart", "attack"),
        listOf("atake", "sa", "puso"),
    )
    private val safePhrases = listOf(
        listOf("no", "help", "needed"),
        listOf("all", "safe"),
        listOf("we", "are", "safe"),
        listOf("were", "safe"),
        listOf("walang", "sugat"),
        listOf("okay", "na"),
        listOf("ayos", "na"),
        listOf("ligtas", "na"),
        listOf("luwas", "na"),
    )

    private val needOrder = listOf(
        // "tubing" is a common Whisper miss for Tagalog "tubig".
        setOf("tubig", "tubing", "water", "inom", "uhaw", "giuhaw") to "water",
        setOf("pagkain", "pagkaon", "food", "gutom", "hungry", "gigutom") to "food",
        setOf("gamot", "tambal", "medicine", "medisina") to "medicine",
        setOf("shelter", "silungan", "bubong") to "shelter",
        setOf("evacuate", "evacuation", "lumikas", "likas", "molikas") to "evacuation",
    )

    fun triage(raw: String): TriageResult {
        val normalized = normalize(raw)
        if (normalized.isBlank()) {
            return TriageResult(Urgency.NEEDS_HELP, "Empty message", actionable = false)
        }
        val tokens = normalized.split(" ")
        val trapped = hasUnnegated(tokens, trappedWords) || hasPhrase(tokens, trappedPhrases)
        val injured = hasUnnegated(tokens, injuredWords)
        val fire = hasUnnegated(tokens, fireWords)
        val drowning = hasUnnegated(tokens, drownWords)
        val notBreathing = hasWordsBetween(tokens, "hindi", "humihinga", maxBetween = 2) ||
            hasWordsBetween(tokens, "di", "humihinga", maxBetween = 2)
        val severe = hasUnnegated(tokens, severeWords) || hasPhrase(tokens, severePhrases) || notBreathing
        val flood = hasUnnegated(tokens, floodWords)
        val help = hasUnnegated(tokens, helpWords) || flood || negatedSafe(tokens)
        val safe = affirmativeSafe(tokens)
        val critical = trapped || injured || fire || drowning || severe

        val urgency = when {
            critical -> Urgency.CRITICAL
            safe && !help -> Urgency.SAFE
            else -> Urgency.NEEDS_HELP
        }

        val summary = if (urgency == Urgency.SAFE) {
            val place = findPlace(normalized)
            if (place != null) "reports safe, $place" else "reports safe"
        } else {
            buildAlertSummary(tokens, normalized, trapped, injured, fire, drowning, severe, flood, critical)
        }
        return TriageResult(urgency, summary, actionable = true)
    }

    private fun buildAlertSummary(
        tokens: List<String>,
        normalized: String,
        trapped: Boolean,
        injured: Boolean,
        fire: Boolean,
        drowning: Boolean,
        severe: Boolean,
        flood: Boolean,
        critical: Boolean,
    ): String {
        val situation = when {
            trapped -> "trapped"
            injured -> "injured"
            fire -> "fire"
            drowning -> "drowning"
            severe -> "medical emergency"
            flood -> "flood"
            else -> null
        }
        val anchors = tokens.mapIndexedNotNull { index, token ->
            if (token in peopleWords || token in trappedWords || token in injuredWords) index else null
        }.toSet()
        val count = extractCount(tokens, anchors)
        val needs = linkedSetOf<String>()
        for ((words, label) in needOrder) {
            val hit = tokens.withIndex().any { (index, token) ->
                token in words && tokens.getOrNull(index - 1) !in negations
            }
            if (hit) needs += label
        }
        if (critical && needs.isEmpty()) needs += "rescue"

        val head = when {
            count != null && situation != null -> "$count $situation"
            count != null -> "$count people"
            situation != null -> situation
            else -> null
        }
        val parts = mutableListOf<String>()
        if (head != null) parts += head
        if (needs.isNotEmpty()) parts += "need " + needs.joinToString(" and ")
        findPlace(normalized)?.let { parts += it }
        if (parts.isEmpty()) {
            val compact = normalized.trim()
            return if (compact.length <= 80) compact else compact.take(77) + "..."
        }
        return parts.joinToString(", ")
    }

    private fun extractCount(tokens: List<String>, anchors: Set<Int>): Int? {
        if (anchors.isEmpty()) return null
        var best: Pair<Int, Int>? = null
        var bestDistance = Int.MAX_VALUE
        for ((index, token) in tokens.withIndex()) {
            val value = numberWords[token] ?: continue
            if (value <= 0 || isPlaceNumber(tokens, index)) continue
            val distance = anchors.minOf { kotlin.math.abs(it - index) }
            if (distance < bestDistance) {
                bestDistance = distance
                best = index to value
            }
        }
        return if (best != null && bestDistance <= 4) best.second else null
    }

    private fun isPlaceNumber(tokens: List<String>, index: Int): Boolean {
        val prev = tokens.getOrNull(index - 1) ?: return false
        return prev in placeNumberPrev
    }

    private fun findPlace(normalized: String): String? {
        val tokens = normalized.split(" ")
        for ((index, token) in tokens.withIndex()) {
            when {
                token == "purok" || token == "purok#" -> {
                    val next = tokens.getOrNull(index + 1) ?: continue
                    return purokLabel(next)
                }
                token.startsWith("purok") && token.length > 5 -> return purokLabel(token.removePrefix("purok"))
            }
        }
        val barangay = Regex("""\b(?:barangay|brgy)\s+([a-z0-9]+(?:\s+[a-z0-9]+){0,2})""").find(normalized)
        if (barangay != null) {
            return "Barangay ${titleCase(trimPlace(barangay.groupValues[1]))}"
        }
        val sitio = Regex("""\bsitio\s+([a-z0-9]+(?:\s+[a-z0-9]+){0,2})""").find(normalized)
        if (sitio != null) {
            return "Sitio ${titleCase(trimPlace(sitio.groupValues[1]))}"
        }
        val zone = Regex("""\bzone\s+(\d+)""").find(normalized)
        if (zone != null) return "Zone ${zone.groupValues[1]}"
        return null
    }

    private fun purokLabel(token: String): String {
        val cleaned = token.trim('#')
        val number = numberWords[cleaned]
        return if (number != null && number > 0) "Purok $number" else "Purok ${titleCase(cleaned)}"
    }

    private fun trimPlace(raw: String): String {
        val parts = raw.split(" ").filter { it.isNotBlank() }.toMutableList()
        while (parts.size > 1 && parts.last() in placeStop) parts.removeAt(parts.lastIndex)
        return parts.joinToString(" ")
    }

    private fun titleCase(value: String): String =
        value.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
            }
        }

    private fun affirmativeSafe(tokens: List<String>): Boolean {
        if (hasPhrase(tokens, safePhrases)) {
            val phraseStart = indexOfAnyPhrase(tokens, safePhrases)
            if (phraseStart != null && tokens.getOrNull(phraseStart - 1) !in negations) return true
        }
        for (index in tokens.indices) {
            if (tokens[index] in safeWords && tokens.getOrNull(index - 1) !in negations) return true
        }
        return false
    }

    private fun negatedSafe(tokens: List<String>): Boolean {
        for (index in tokens.indices) {
            if (tokens[index] in safeWords && tokens.getOrNull(index - 1) in negations) return true
        }
        return false
    }

    private fun hasUnnegated(tokens: List<String>, words: Set<String>): Boolean {
        for (index in tokens.indices) {
            if (tokens[index] in words && tokens.getOrNull(index - 1) !in negations) return true
        }
        return false
    }

    private fun hasPhrase(tokens: List<String>, phrases: List<List<String>>): Boolean =
        indexOfAnyPhrase(tokens, phrases) != null

    /** [start] and [end] with at most [maxBetween] tokens between them, in that order. */
    private fun hasWordsBetween(tokens: List<String>, start: String, end: String, maxBetween: Int): Boolean {
        for (index in tokens.indices) {
            if (tokens[index] != start) continue
            val last = minOf(tokens.lastIndex, index + 1 + maxBetween)
            for (cursor in (index + 1)..last) {
                if (tokens[cursor] == end) return true
            }
        }
        return false
    }

    private fun indexOfAnyPhrase(tokens: List<String>, phrases: List<List<String>>): Int? {
        for (phrase in phrases) {
            if (phrase.size > tokens.size) continue
            for (start in 0..tokens.size - phrase.size) {
                if (tokens.subList(start, start + phrase.size) == phrase) return start
            }
        }
        return null
    }

    private fun normalize(raw: String): String =
        raw.lowercase(Locale.US)
            .replace("'", "")
            .replace(Regex("[^a-z0-9ñ\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
