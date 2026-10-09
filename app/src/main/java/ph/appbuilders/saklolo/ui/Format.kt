package ph.appbuilders.saklolo.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.triage.Urgency

/** Clock time on a sent bubble, such as 9:13 AM. */
fun messageClock(millis: Long): String =
    SimpleDateFormat("h:mm a", Locale.US).format(Date(millis))

fun formatWhen(millis: Long, now: Long = System.currentTimeMillis()): String {
    val delta = (now - millis).coerceAtLeast(0)
    val minutes = delta / 60_000
    return when {
        delta < 60_000 -> "just now"
        minutes < 60 -> if (minutes == 1L) "1 min ago" else "$minutes min ago"
        delta < 86_400_000 -> {
            val hours = delta / 3_600_000
            if (hours == 1L) "1 hr ago" else "$hours hr ago"
        }
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(millis))
    }
}

fun formatGps(lat: Double?, lon: Double?): String {
    if (lat == null || lon == null) return "No GPS"
    return String.format(Locale.US, "GPS %.2f, %.2f", lat, lon)
}

fun formatHops(hops: Int): String = when (hops) {
    0 -> "recorded here"
    1 -> "1 hop"
    else -> "$hops hops"
}

fun phonesNearby(count: Int): String = when (count) {
    1 -> "1 phone nearby"
    else -> "$count phones nearby"
}

fun formatNearby(count: Int): String = when (count) {
    1 -> "Offline · 1 phone nearby"
    else -> "Offline · $count phones nearby"
}

fun formatCounts(alerts: List<Alert>): String {
    val critical = alerts.count { it.urgency == Urgency.CRITICAL }
    val help = alerts.count { it.urgency == Urgency.NEEDS_HELP }
    val safe = alerts.count { it.urgency == Urgency.SAFE }
    return "$critical Critical · $help Help · $safe Safe"
}

fun formatDelivered(count: Int): String =
    if (count == 1) "Delivered to 1 phone" else "Delivered to $count phones"

fun sourceLabel(source: String): String =
    if (source == "AI") "On-device AI" else "Keyword rules"

fun statusPill(gemmaLoading: Boolean, peers: Int): String =
    if (gemmaLoading) "AI loading…" else "Offline · $peers nearby"

fun locationPill(lat: Double?, lon: Double?): String {
    if (lat == null || lon == null) return "No GPS"
    return String.format(Locale.US, "%.2f, %.2f · GPS", lat, lon)
}

fun holdLabel(recording: Boolean, elapsedSec: Int): String =
    if (!recording) "Hold" else "Hold · %d:%02d".format(elapsedSec / 60, elapsedSec % 60)

/**
 * Recorded means this phone made the clip. Sent means this phone queued it to at
 * least one peer. Delivered stays false: there is no peer receipt yet.
 */
data class AlertProgress(val recorded: Boolean, val sent: Boolean, val delivered: Boolean)

fun alertProgress(localOrigin: Boolean, deliveredCount: Int): AlertProgress = AlertProgress(
    recorded = localOrigin,
    sent = deliveredCount > 0,
    delivered = false,
)
