package ph.appbuilders.saklolo.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ph.appbuilders.saklolo.triage.Urgency

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Fields that travel over Nearby or stay in the on-disk log. Audio stays local. */
@Serializable
data class WireAlert(
    val id: String,
    val transcript: String,
    val summary: String,
    val urgency: Urgency,
    val createdAtMillis: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val hops: Int = 0,
    val language: String = "AUTO",
    val summarySource: String = "RULES",
)

@Serializable
data class ClipLink(
    val alertId: String,
    val payloadId: Long,
)

@Serializable
data class AlertEnvelope(
    val v: Int = 2,
    val alerts: List<WireAlert> = emptyList(),
    val clips: List<ClipLink> = emptyList(),
)

data class RelayPacket(
    val alerts: List<Alert> = emptyList(),
    val clips: List<ClipLink> = emptyList(),
)

@Serializable
data class AlertFile(
    val alerts: List<Alert> = emptyList(),
    val localOriginIds: List<String> = emptyList(),
)

object AlertJson {
    fun encodeEnvelope(alerts: List<Alert>, clips: List<ClipLink> = emptyList()): String =
        json.encodeToString(AlertEnvelope(alerts = alerts.map { it.toWire() }, clips = clips))

    fun decodeEnvelope(payload: String): RelayPacket {
        val envelope = json.decodeFromString(AlertEnvelope.serializer(), payload)
        return RelayPacket(
            alerts = envelope.alerts.map { it.toAlert() },
            clips = envelope.clips,
        )
    }

    fun encodeFile(alerts: List<Alert>, localOriginIds: Set<String>): String =
        json.encodeToString(AlertFile(alerts, localOriginIds.toList()))

    fun decodeFile(payload: String): AlertFile =
        json.decodeFromString(AlertFile.serializer(), payload)
}

private fun Alert.toWire() = WireAlert(
    id = id,
    transcript = transcript,
    summary = summary,
    urgency = urgency,
    createdAtMillis = createdAtMillis,
    lat = lat,
    lon = lon,
    hops = hops,
    language = language,
    summarySource = summarySource,
)

private fun WireAlert.toAlert() = Alert(
    id = id,
    transcript = transcript,
    summary = summary,
    urgency = urgency,
    createdAtMillis = createdAtMillis,
    lat = lat,
    lon = lon,
    hops = hops,
    language = language,
    summarySource = summarySource,
)
