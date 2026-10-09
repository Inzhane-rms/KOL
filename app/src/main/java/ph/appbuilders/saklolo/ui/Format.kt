package ph.appbuilders.saklolo.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatWhen(millis: Long, now: Long = System.currentTimeMillis()): String {
    val delta = (now - millis).coerceAtLeast(0)
    return when {
        delta < 60_000 -> "just now"
        delta < 3_600_000 -> "${delta / 60_000}m ago"
        delta < 86_400_000 -> "${delta / 3_600_000}h ago"
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(millis))
    }
}

fun formatGps(lat: Double?, lon: Double?): String {
    if (lat == null || lon == null) return "No GPS"
    return String.format(Locale.US, "%.5f, %.5f", lat, lon)
}
