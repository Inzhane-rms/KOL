package ph.appbuilders.saklolo.triage

data class SummaryChoice(
    val summary: String,
    val source: String,
)

/**
 * Picks the one-line summary shown on an alert. Urgency never comes from here.
 * A model line is used only when it looks like a single responder sentence.
 */
object SummaryRefine {
    const val AI = "AI"
    const val RULES = "RULES"

    fun choose(rulesSummary: String, modelOutput: String?): SummaryChoice {
        val line = firstLine(modelOutput)
        if (!isUsable(line)) return SummaryChoice(rulesSummary, RULES)
        return SummaryChoice(line.take(140).trim(), AI)
    }

    private fun firstLine(modelOutput: String?): String {
        val raw = modelOutput
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            .orEmpty()
        return raw
            .removePrefix("Summary:")
            .removePrefix("summary:")
            .removePrefix("-")
            .trim()
            .trim('"', '\'', '“', '”')
            .trim()
    }

    fun isUsable(text: String): Boolean {
        if (text.length !in 8..140) return false
        if (text.any { it == '\n' || it == '\r' }) return false
        val lower = text.lowercase()
        if (lower.contains("transcript:")) return false
        if (lower.startsWith("i cannot") || lower.startsWith("i can't") || lower.startsWith("as an ai")) {
            return false
        }
        return text.count { it.isLetter() } >= 6
    }
}
