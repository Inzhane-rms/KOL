package ph.appbuilders.saklolo.model

import kotlinx.serialization.Serializable
import ph.appbuilders.saklolo.triage.Urgency

@Serializable
data class Alert(
    val id: String,
    val transcript: String,
    val summary: String,
    val urgency: Urgency,
    val createdAtMillis: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val hops: Int = 0,
    val language: String = "AUTO",
    /** "AI" when Gemma refined the line, otherwise "RULES". Urgency is always rules. */
    val summarySource: String = "RULES",
    /** Local wav path. Never put this on the wire. */
    val audioPath: String? = null,
    /** How many nearby phones this device handed the alert to. Local only. */
    val deliveredCount: Int = 0,
)

fun List<Alert>.sortedForFeed(): List<Alert> =
    sortedWith(compareBy<Alert> { it.urgency.rank }.thenByDescending { it.createdAtMillis })
