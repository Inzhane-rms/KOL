package ph.appbuilders.saklolo.contact

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** How the user answered. Each one hides the chips for that heard line. */
enum class ReplyChannel { CHIP, TEXT, VOICE }

/**
 * Chips stay up until this phone replies to that line.
 * A later line from the other person shows chips again.
 */
class ReplySession {
    private var hidden by mutableStateOf("")

    fun offer(scope: String, heard: String): String {
        val key = heard.trim()
        if (key.isEmpty() || hidden == token(scope, key)) return ""
        return key
    }

    fun replied(scope: String, heard: String, channel: ReplyChannel) {
        val key = heard.trim()
        if (key.isEmpty()) return
        hidden = token(scope, key)
    }

    private fun token(scope: String, heard: String) = "$scope\u0000$heard"
}

/** A second tap inside the window does not send another message. */
class ChipTapGuard(private val windowMs: Long = WINDOW_MS) {
    private var lastAt = Long.MIN_VALUE

    fun allow(now: Long): Boolean {
        if (lastAt != Long.MIN_VALUE && now - lastAt < windowMs) return false
        lastAt = now
        return true
    }

    companion object {
        const val WINDOW_MS = 600L
    }
}

/** Newest replies stay. About 32 entries, then the least recently used one drops. */
class ReplyCache(private val max: Int = 32) {
    private val rows = object : LinkedHashMap<String, List<ReplyChip>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ReplyChip>>?): Boolean = size > max
    }

    fun get(key: String): List<ReplyChip>? = synchronized(rows) { rows[key] }

    fun put(key: String, chips: List<ReplyChip>): Map<String, List<ReplyChip>> = synchronized(rows) {
        rows[key] = chips
        LinkedHashMap(rows)
    }

    fun size(): Int = synchronized(rows) { rows.size }

    fun clear() = synchronized(rows) { rows.clear() }
}
