package ph.appbuilders.saklolo.contact

/**
 * One ring per invite. Back, the 30s timeout, and a remote decline or end all
 * mark that peer's open invites handled, so the next message cannot ring again.
 */
class RingLoop(var now: Long = 0L) {
    var call: CallState = CallState()
        private set

    private val handled = HashSet<String>()
    private val rung = HashSet<String>()
    private val pending = ArrayList<Outgoing>()
    private var token = 0
    private var armedToken = 0
    private var dueAt = 0L

    data class Outgoing(val peerId: String, val kind: String)

    fun drain(): List<Outgoing> {
        val copy = pending.toList()
        pending.clear()
        return copy
    }

    fun adopt(state: CallState) {
        call = state
    }

    fun armToken(): Int = armedToken

    fun due(seen: Int): Boolean = seen == armedToken && dueAt > 0L && now >= dueAt

    fun inviteOut(peerId: String, name: String) {
        call = CallMachine.inviteOut(peerId, name)
        arm()
    }

    fun accept(messages: List<DirectMessage>, myId: String) {
        val peer = call.peerId
        val ringing = call.phase == CallPhase.INCOMING || call.phase == CallPhase.OUTGOING
        disarm()
        if (peer.isNotBlank()) markInvites(messages, peer, myId, Long.MAX_VALUE)
        call = CallMachine.accept(call)
        if (ringing && peer.isNotBlank()) pending += Outgoing(peer, Ptt.ACCEPT)
    }

    fun decline(messages: List<DirectMessage>, myId: String) {
        val peer = call.peerId
        val notify = Hangup.notify(call.phase) != null
        disarm()
        if (peer.isNotBlank()) markInvites(messages, peer, myId, Long.MAX_VALUE)
        if (notify && peer.isNotBlank()) pending += Outgoing(peer, Ptt.DECLINE)
        call = CallMachine.decline(call)
    }

    fun end(messages: List<DirectMessage>, myId: String) {
        val peer = call.peerId
        val kind = Hangup.notify(call.phase)
        disarm()
        if (peer.isNotBlank()) markInvites(messages, peer, myId, Long.MAX_VALUE)
        if (kind != null && peer.isNotBlank()) pending += Outgoing(peer, kind)
        call = CallMachine.end(call)
    }

    fun fireTimeout(messages: List<DirectMessage>, myId: String): Boolean {
        if (call.phase != CallPhase.OUTGOING && call.phase != CallPhase.INCOMING) return false
        if (!due(armedToken)) return false
        end(messages, myId)
        return true
    }

    /** True only when this pass armed a new ring that is still ringing. */
    fun absorb(messages: List<DirectMessage>, myId: String): Boolean {
        var startRing = false
        for (message in messages.sortedBy { it.createdAtMillis }) {
            if (message.kind !in CALL_KINDS) continue
            if (message.toDeviceId != myId) continue
            if (message.id in handled) continue
            when (message.kind) {
                Ptt.INVITE -> {
                    if (now - message.createdAtMillis > INVITE_MS) {
                        handled += message.id
                    } else {
                        val wasIdle = call.phase == CallPhase.IDLE
                        call = CallMachine.inviteIn(call, message.fromDeviceId, message.senderName)
                        if (wasIdle && call.phase == CallPhase.INCOMING && message.id !in rung) {
                            rung += message.id
                            arm()
                            startRing = true
                        }
                    }
                }
                Ptt.ACCEPT -> {
                    val next = CallMachine.remoteSignal(call, message, myId)
                    if (next.phase != CallPhase.OUTGOING && next.phase != CallPhase.INCOMING) disarm()
                    call = next
                    handled += message.id
                    if (next.phase != CallPhase.INCOMING && next.phase != CallPhase.OUTGOING) startRing = false
                }
                Ptt.DECLINE, Ptt.END -> {
                    markInvites(messages, message.fromDeviceId, myId, message.createdAtMillis)
                    val next = CallMachine.remoteSignal(call, message, myId)
                    if (next.phase != CallPhase.OUTGOING && next.phase != CallPhase.INCOMING) disarm()
                    call = next
                    handled += message.id
                    startRing = false
                }
            }
        }
        return startRing && (call.phase == CallPhase.INCOMING || call.phase == CallPhase.OUTGOING)
    }

    private fun markInvites(messages: List<DirectMessage>, peerId: String, myId: String, atOrBefore: Long) {
        if (peerId.isBlank()) return
        for (message in messages) {
            if (
                message.kind == Ptt.INVITE &&
                message.fromDeviceId == peerId &&
                message.toDeviceId == myId &&
                message.createdAtMillis <= atOrBefore
            ) {
                handled += message.id
                rung += message.id
            }
        }
    }

    private fun arm() {
        token += 1
        armedToken = token
        dueAt = now + Hangup.RING_MS
    }

    private fun disarm() {
        token += 1
        armedToken = token
        dueAt = 0L
    }

    private companion object {
        const val INVITE_MS = 120_000L
        val CALL_KINDS = setOf(Ptt.INVITE, Ptt.ACCEPT, Ptt.DECLINE, Ptt.END)
    }
}
