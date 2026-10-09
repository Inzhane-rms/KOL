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
)

fun List<Alert>.sortedForFeed(): List<Alert> =
    sortedWith(compareBy<Alert> { it.urgency.rank }.thenByDescending { it.createdAtMillis })
