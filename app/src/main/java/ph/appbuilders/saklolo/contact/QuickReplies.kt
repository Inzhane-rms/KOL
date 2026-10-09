package ph.appbuilders.saklolo.contact

import ph.appbuilders.saklolo.triage.TriageEngine

data class ReplyChip(
    val label: String,
    val sendText: String,
    val fill: Boolean,
)

/**
 * Short Tagalog replies for the last thing the other person said.
 * Rules always produce the chips. A loaded Gemma model may replace them
 * only when its text parses as two or three short lines.
 */
object QuickReplies {
    const val GATE_LABEL = "Nandito ako sa Gate __"
    const val GATE_PREFILL = "Nandito ako sa Gate "

    private val heardKinds = setOf("text", "voice", "call_clip", "urgent")

    fun fromRules(text: String): List<ReplyChip> {
        val heard = text.trim()
        if (heard.isEmpty()) return emptyList()
        val folded = heard.lowercase()
        if (folded.contains("nasaan ka") || folded.contains("saan ka")) {
            return listOf(
                send("Papunta na ako"),
                ReplyChip(GATE_LABEL, GATE_PREFILL, fill = true),
                send("Saan tayo magkita?"),
            )
        }
        if (folded.contains("ok ka lang") || folded.contains("okay ka lang")) {
            return listOf(send("Ok lang ako"), send("Hindi, tulungan mo ako"))
        }
        if (TriageEngine.mentionsEmergency(heard)) {
            return listOf(send("Papunta na ako, 2 min"), send("Tatawag ako ng medic"))
        }
        return listOf(send("Sige"), send("Sandali lang"), send("Nasaan ka?"))
    }

    /** Null when the model text is missing, too long, or not two or three lines. */
    fun acceptModel(raw: String?): List<ReplyChip>? {
        if (raw.isNullOrBlank()) return null
        val lines = raw.lineSequence()
            .map { clean(it) }
            .filter { it.isNotEmpty() && it.length <= 48 }
            .take(4)
            .toList()
        if (lines.size !in 2..3) return null
        return lines.map { line ->
            if (line.contains("Gate", ignoreCase = true) && line.contains("_")) {
                val prefill = line.replace("_", "").replace(Regex("\\s+"), " ").trim() + " "
                ReplyChip(GATE_LABEL, prefill, fill = true)
            } else {
                send(line)
            }
        }
    }

    fun latestHeard(messages: List<DirectMessage>, myId: String): String =
        messages.lastOrNull { it.fromDeviceId != myId && it.kind in heardKinds }?.body?.trim().orEmpty()

    private fun send(label: String) = ReplyChip(label, label, fill = false)

    private fun clean(line: String): String =
        line.trim().replace(Regex("^(?:[-*•]|\\d+[.)])\\s*"), "").trim()
}
