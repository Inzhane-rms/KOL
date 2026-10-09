package ph.appbuilders.saklolo.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class AlertEnvelope(
    val v: Int = 1,
    val alerts: List<Alert> = emptyList(),
)

@Serializable
data class AlertFile(
    val alerts: List<Alert> = emptyList(),
    val localOriginIds: List<String> = emptyList(),
)

object AlertJson {
    fun encodeEnvelope(alerts: List<Alert>): String = json.encodeToString(AlertEnvelope(alerts = alerts))

    fun decodeEnvelope(payload: String): List<Alert> =
        json.decodeFromString(AlertEnvelope.serializer(), payload).alerts

    fun encodeFile(alerts: List<Alert>, localOriginIds: Set<String>): String =
        json.encodeToString(AlertFile(alerts, localOriginIds.toList()))

    fun decodeFile(payload: String): AlertFile =
        json.decodeFromString(AlertFile.serializer(), payload)
}
