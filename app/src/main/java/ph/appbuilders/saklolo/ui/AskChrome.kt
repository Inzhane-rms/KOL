package ph.appbuilders.saklolo.ui

import ph.appbuilders.saklolo.ask.AskSuggestion

/** Amber "Pinakamalapit na tip" is only for a Gemma nearest-pair fallback. */
internal fun showNearestTipLabel(fromModel: Boolean): Boolean = fromModel

/** A suggestion chip sends the stored bank question, not its short label. */
internal fun chipQuestion(suggestion: AskSuggestion): String = suggestion.question.trim()

/** Source line under an answer. Uses the bank's real source text for each code. */
internal fun realSourceLine(sources: Map<String, String>, sourceCode: String): String {
    val text = sourceCode.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { code -> sources[code]?.trim().orEmpty().ifEmpty { code } }
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
    return if (text.isEmpty()) "" else "Source: $text"
}
