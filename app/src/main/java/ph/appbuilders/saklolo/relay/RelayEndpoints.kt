package ph.appbuilders.saklolo.relay

/**
 * Connected and pending Nearby endpoints. Every read and write takes [lock].
 * [NearbyRelay.send] and the Nearby callbacks go through this object, so they
 * share one lock and never touch the sets directly.
 */
class RelayEndpoints {
    private val lock = Any()
    private val connected = LinkedHashMap<String, NearbyPeer>()
    private val pending = LinkedHashSet<String>()
    private val names = HashMap<String, String>()
    private var running = false
    private var localName = "B-LINK"
    private var filter = PeerFilter()

    enum class SessionStart { FRESH, RENAME, UNCHANGED }

    fun beginSession(name: String): SessionStart = synchronized(lock) {
        val trimmed = name.trim().ifEmpty { "B-LINK" }
        val changed = localName != trimmed
        localName = trimmed
        when {
            !running -> {
                running = true
                SessionStart.FRESH
            }
            changed -> SessionStart.RENAME
            else -> SessionStart.UNCHANGED
        }
    }

    fun localName(): String = synchronized(lock) { localName }

    fun setFilter(value: PeerFilter) {
        synchronized(lock) { filter = value }
    }

    fun allows(name: String): Boolean = synchronized(lock) { filter.allows(name) }

    fun isRunning(): Boolean = synchronized(lock) { running }

    /** @return true when this endpoint was newly marked pending */
    fun tryBeginConnect(endpointId: String, name: String): Boolean = synchronized(lock) {
        if (!running || !filter.allows(name)) return false
        if (endpointId in connected || endpointId in pending) return false
        names[endpointId] = name
        pending.add(endpointId)
        true
    }

    fun rememberName(endpointId: String, name: String) = synchronized(lock) {
        if (name.isNotBlank()) names[endpointId] = name
    }

    fun markConnected(endpointId: String, fallbackName: String, now: Long) = synchronized(lock) {
        pending.remove(endpointId)
        val name = names[endpointId] ?: fallbackName.ifBlank { "Nearby phone" }
        connected[endpointId] = NearbyPeer(endpointId, name, now)
    }

    fun markConnectFailed(endpointId: String) = synchronized(lock) {
        pending.remove(endpointId)
    }

    fun markDisconnected(endpointId: String) = synchronized(lock) {
        connected.remove(endpointId)
        pending.remove(endpointId)
    }

    fun onLost(endpointId: String) = synchronized(lock) {
        pending.remove(endpointId)
    }

    fun snapshot(exceptEndpoint: String? = null): List<NearbyPeer> = synchronized(lock) {
        connected.values.filter { it.endpointId != exceptEndpoint }
    }

    fun clear() = synchronized(lock) {
        running = false
        connected.clear()
        pending.clear()
    }

    fun pendingCount(): Int = synchronized(lock) { pending.size }

    fun connectedCount(): Int = synchronized(lock) { connected.size }
}

data class NearbyPeer(
    val endpointId: String,
    val name: String,
    val connectedAtMillis: Long,
)
