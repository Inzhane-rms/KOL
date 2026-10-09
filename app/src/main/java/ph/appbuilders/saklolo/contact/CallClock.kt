package ph.appbuilders.saklolo.contact

/** A new call gets a new id. Accepting the same call keeps it. Idle clears it. */
object CallSession {
    fun nextId(wasIdle: Boolean, nowIdle: Boolean, currentId: Long): Long = when {
        nowIdle -> 0L
        wasIdle -> currentId + 1L
        else -> currentId
    }
}

/** The on-call clock. It starts at 0:00 for each call id and only runs while active. */
object CallClock {
    fun anchor(active: Boolean, callId: Long, previousAnchor: Long, now: Long): Long {
        if (!active || callId == 0L) return 0L
        return if (previousAnchor == 0L) now else previousAnchor
    }

    fun label(elapsedMs: Long): String {
        val seconds = elapsedMs.coerceAtLeast(0L) / 1000L
        return "%d:%02d".format(seconds / 60L, seconds % 60L)
    }
}
