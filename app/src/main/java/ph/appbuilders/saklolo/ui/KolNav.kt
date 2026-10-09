package ph.appbuilders.saklolo.ui

import ph.appbuilders.saklolo.contact.CaptionDisplay
import ph.appbuilders.saklolo.contact.ContactRow
import ph.appbuilders.saklolo.contact.DirectMessage

/** Tab order, motion, and the real rows the Home screen can show. */
object KolNav {
    const val MOTION_MS = 220
    const val HOLD_TO_TALK = "Hold to talk"
    const val IN_RANGE = "In range"
    const val WAITING = "Waiting"
    const val SENT = "Sent"
    const val SEND_URGENT = "Send urgent message"

    /** The tab under a chat stays put. A chat itself is remembered for the call. */
    fun backTarget(route: String, current: String): String = when {
        route == MainNav.THREAD -> MainNav.THREAD
        showsBar(route) -> route
        else -> current
    }

    /** A call returns to the route it started from. A blank or unknown route lands on Home. */
    fun afterCall(startedAt: String): String = when (startedAt) {
        MainNav.HOME, MainNav.CONTACTS, MainNav.MESSAGES, MainNav.ADD, MainNav.THREAD -> startedAt
        else -> MainNav.HOME
    }

    /**
     * Bar is 304dp, padding 8+8, three 8dp gaps, three 48dp idle slots.
     * Room for the pill is 120, so 124 does not fit.
     */
    fun navPillWidth(): Int {
        val room = 304 - (8 * 2) - (8 * 3) - (48 * 3)
        return if (room >= 124) 124 else 120
    }

    fun showsBar(route: String): Boolean = route != MainNav.CALL && route != MainNav.THREAD

    fun stateKey(route: String, peerId: String): String =
        if (route == MainNav.THREAD) "thread:$peerId" else route

    fun isPush(route: String): Boolean = route == MainNav.THREAD || route == MainNav.CALL

    /** True when the incoming screen should enter from the right. */
    fun slideFromRight(from: String, to: String): Boolean = when {
        isPush(to) && !isPush(from) -> true
        isPush(from) && !isPush(to) -> false
        else -> tabIndex(to) >= tabIndex(from)
    }

    fun tabIndex(route: String): Int = when (route) {
        MainNav.HOME -> 0
        MainNav.CONTACTS -> 1
        MainNav.MESSAGES, MainNav.THREAD -> 2
        MainNav.ADD -> 3
        else -> 0
    }

    /** Display form of the existing short code. The wire code is unchanged. */
    fun kolCode(shortCode: String): String {
        val suffix = shortCode.substringAfterLast(' ').substringAfter('-').trim().ifBlank { "0000" }
        return "KOL-$suffix"
    }

    fun friendsLine(inRange: Int): String =
        if (inRange == 1) "Offline · 1 friend in range" else "Offline · $inRange friends in range"

    /** Sent follows the stored handoff time. Live range never flips a sent message back to Waiting. */
    fun bubbleStatus(sentAtMillis: Long, time: String): String =
        if (sentAtMillis > 0L) "$SENT · $time" else WAITING

    fun homeStatus(sentAtMillis: Long, name: String): String =
        if (sentAtMillis > 0L) "$SENT · $name" else "$WAITING · $name"

    fun homeBackLabel(setupMissing: Int, waitingPeers: Int, lastHeardMillis: Long, now: Long): String = when {
        setupMissing > 0 -> "Setup needed"
        waitingPeers == 1 -> "1 queued"
        waitingPeers > 1 -> "$waitingPeers queued"
        lastHeardMillis > 0L -> "Last heard · ${formatWhen(lastHeardMillis, now)}"
        else -> "Nothing queued"
    }

    @Suppress("UNUSED_PARAMETER")
    fun waitingPeers(messages: List<DirectMessage>, contacts: List<ContactRow>, myId: String): Int {
        return messages.mapNotNull { message ->
            val outgoing = message.localOrigin || message.fromDeviceId == myId
            if (!outgoing || message.sentAtMillis > 0L) null else message.toDeviceId.takeIf { it.isNotBlank() }
        }.distinct().size
    }

    fun recentActivity(
        messages: List<DirectMessage>,
        contacts: List<ContactRow>,
        myId: String,
    ): List<KolActivity> {
        val rows = mutableListOf<KolActivity>()
        messages.filter { it.kind == "voice" || it.kind == "call_clip" }
            .maxByOrNull { it.createdAtMillis }
            ?.let { message ->
                val peerId = peerOf(message, myId)
                val heard = if (message.fromDeviceId == peerId) message.senderName else ""
                val name = peerName(peerId, contacts, heard)
                rows += KolActivity(
                    title = "Voice note · $name",
                    detail = CaptionDisplay.text(message.body).ifBlank { "Transcript on this phone" },
                    tone = "voice",
                    at = message.createdAtMillis,
                    peerId = peerId,
                )
            }
        messages.filter { (it.localOrigin || it.fromDeviceId == myId) && it.kind == "text" }
            .maxByOrNull { it.createdAtMillis }
            ?.let { message ->
                val name = peerName(message.toDeviceId, contacts, "")
                val handedOff = message.sentAtMillis > 0L
                rows += KolActivity(
                    title = homeStatus(message.sentAtMillis, name),
                    detail = CaptionDisplay.text(message.body),
                    tone = if (handedOff) "sent" else "waiting",
                    at = message.createdAtMillis,
                    peerId = message.toDeviceId,
                )
            }
        contacts.filter { it.saved && it.addedAtMillis > 0L }
            .maxByOrNull { it.addedAtMillis }
            ?.let { contact ->
                rows += KolActivity(
                    title = "Contact added · ${contact.name}",
                    detail = contact.name,
                    tone = "added",
                    at = contact.addedAtMillis,
                    peerId = contact.deviceId,
                )
            }
        return rows.sortedByDescending { it.at }.take(4)
    }

    private fun peerOf(message: DirectMessage, myId: String): String =
        if (message.localOrigin || message.fromDeviceId == myId) message.toDeviceId else message.fromDeviceId

    private fun peerName(deviceId: String, contacts: List<ContactRow>, senderName: String): String {
        val saved = contacts.firstOrNull { it.deviceId == deviceId }?.name?.trim().orEmpty()
        if (saved.isNotEmpty()) return saved
        val heard = senderName.trim()
        return heard.ifBlank { "Friend" }
    }
}

data class KolActivity(
    val title: String,
    val detail: String,
    val tone: String,
    val at: Long,
    val peerId: String,
)

/** Live labels. The copy test reads these and strings.xml. */
object KolCopy {
    val LABELS = listOf(
        "KOL",
        KolNav.HOLD_TO_TALK,
        KolNav.IN_RANGE,
        KolNav.WAITING,
        KolNav.SENT,
        KolNav.SEND_URGENT,
        "View all",
        "Scan QR",
        "Share my code",
        "Possible emergency",
        "Add a friend",
        "In range now",
        "Live captions · on-device",
        "AI transcript",
        "Scan their contact QR",
        "That QR is not a KOL contact code",
        "Ready to connect",
    )
}
