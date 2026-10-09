package ph.appbuilders.saklolo.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.triage.Urgency

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
