package ph.appbuilders.saklolo.relay

import ph.appbuilders.saklolo.model.Alert

/**
 * Store-and-forward rules.
 *
 * The phone that records an alert keeps hops = 0 and sends that 0 unchanged.
 * Each phone that receives a copy stores hops + 1. A direct receipt is therefore
 * 1 hop. Forwarding does not increment again. An alert is not stored when the
 * incremented count would pass [MAX_HOPS], and it is not forwarded once the
 * stored count has reached [MAX_HOPS].
 */
object RelayPolicy {
    const val MAX_HOPS = 5

    /** Copy stored on receive, or null when the id is blank or the next hop is too far. */
    fun receive(alert: Alert): Alert? {
        if (alert.id.isBlank()) return null
        val hop = alert.hops + 1
        if (hop > MAX_HOPS) return null
        return alert.copy(hops = hop)
    }

    /** Forward the stored hop count unchanged. */
    fun outgoing(alert: Alert): Alert? {
        if (alert.hops >= MAX_HOPS) return null
        return alert
    }
}
