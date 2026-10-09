package ph.appbuilders.saklolo.relay

import ph.appbuilders.saklolo.model.Alert

/**
 * Store-and-forward rules for the mesh.
 *
 * A newly recorded alert has hops = 0. Each phone that did not create it
 * rebroadcasts a copy with hops + 1. Alerts are not forwarded once the hop
 * count would pass [MAX_HOPS] (about five relays past the sender).
 */
object RelayPolicy {
    const val MAX_HOPS = 5

    fun shouldStore(alert: Alert, knownIds: Set<String>): Boolean {
        if (alert.id.isBlank() || alert.id in knownIds) return false
        return alert.hops in 0..MAX_HOPS
    }

    fun outgoing(alert: Alert, localOrigin: Boolean): Alert? {
        val hop = if (localOrigin) alert.hops else alert.hops + 1
        if (hop > MAX_HOPS) return null
        return alert.copy(hops = hop)
    }
}
