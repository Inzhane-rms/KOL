package ph.appbuilders.saklolo.relay

import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.triage.Urgency
import java.util.Locale

/**
 * Compact QR payload. Full transcripts travel over Nearby; the QR carries the
 * summary a responder can act on if the radio link fails.
 *
 * SKL1|id|URGENCY|millis|lat|lon|hops|summary
 */
object QrCodec {
    private const val PREFIX = "SKL1"

    fun encode(alert: Alert): String {
        val summary = alert.summary.replace("|", "/").replace("\n", " ").take(140)
        val lat = alert.lat?.let { String.format(Locale.US, "%.5f", it) }.orEmpty()
        val lon = alert.lon?.let { String.format(Locale.US, "%.5f", it) }.orEmpty()
        return listOf(
            PREFIX,
            alert.id,
            alert.urgency.name,
            alert.createdAtMillis.toString(),
            lat,
            lon,
            alert.hops.toString(),
            summary,
        ).joinToString("|")
    }

    fun decode(payload: String): Alert? {
        val parts = payload.trim().split("|")
        if (parts.size < 8 || parts[0] != PREFIX) return null
        val urgency = runCatching { Urgency.valueOf(parts[2]) }.getOrNull() ?: return null
        val created = parts[3].toLongOrNull() ?: return null
        val hops = parts[6].toIntOrNull() ?: return null
        if (hops !in 0..RelayPolicy.MAX_HOPS) return null
        val summary = parts.subList(7, parts.size).joinToString("|").trim()
        if (parts[1].isBlank() || summary.isBlank()) return null
        return Alert(
            id = parts[1],
            transcript = summary,
            summary = summary,
            urgency = urgency,
            createdAtMillis = created,
            lat = parts[4].toDoubleOrNull(),
            lon = parts[5].toDoubleOrNull(),
            hops = hops,
            language = "QR",
        )
    }
}
