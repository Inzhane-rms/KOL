package ph.appbuilders.saklolo.model

import ph.appbuilders.saklolo.relay.RelayPolicy
import java.io.File

interface AlertPersistence {
    fun load(): AlertFile
    fun save(alerts: List<Alert>, localOriginIds: Set<String>)
}

class FileAlertPersistence(private val file: File) : AlertPersistence {
    override fun load(): AlertFile {
        if (!file.exists()) return AlertFile()
        return try {
            AlertJson.decodeFile(file.readText())
        } catch (_: Exception) {
            AlertFile()
        }
    }

    override fun save(alerts: List<Alert>, localOriginIds: Set<String>) {
        file.parentFile?.mkdirs()
        file.writeText(AlertJson.encodeFile(alerts, localOriginIds))
    }
}

/**
 * Alert log. Dedupes by id. Received copies are stored with hops + 1.
 * Reloading from disk does not increment again.
 */
class AlertStore(private val persistence: AlertPersistence) {
    constructor(file: File) : this(FileAlertPersistence(file))

    private val alerts = linkedMapOf<String, Alert>()
    private val localOriginIds = mutableSetOf<String>()

    init {
        load()
    }

    fun snapshot(): List<Alert> = synchronized(this) {
        alerts.values.toList().sortedForFeed()
    }

    fun find(id: String): Alert? = synchronized(this) { alerts[id] }

    fun isLocalOrigin(id: String): Boolean = synchronized(this) { id in localOriginIds }

    fun addLocal(alert: Alert) = synchronized(this) {
        alerts[alert.id] = alert
        localOriginIds += alert.id
        persist()
    }

    fun ingest(incoming: List<Alert>): List<Alert> = synchronized(this) {
        val fresh = mutableListOf<Alert>()
        for (alert in incoming) {
            if (alert.id.isBlank() || alert.id in alerts) continue
            val stored = RelayPolicy.receive(alert) ?: continue
            alerts[stored.id] = stored
            fresh += stored
        }
        if (fresh.isNotEmpty()) persist()
        fresh
    }

    fun attachAudio(id: String, path: String) = synchronized(this) {
        val current = alerts[id] ?: return
        if (current.audioPath == path) return
        alerts[id] = current.copy(audioPath = path)
        persist()
    }

    fun markDelivered(id: String, count: Int) = synchronized(this) {
        val current = alerts[id] ?: return
        alerts[id] = current.copy(deliveredCount = count)
        persist()
    }

    fun setResponding(id: String, responding: Boolean) = synchronized(this) {
        val current = alerts[id] ?: return
        if (current.responding == responding) return
        alerts[id] = current.copy(responding = responding)
        persist()
    }

    /** One-time copy of an older log. Does not increment hops. */
    fun importExisting(existing: List<Alert>, origins: Set<String>) = synchronized(this) {
        if (alerts.isNotEmpty()) return
        existing.forEach { alerts[it.id] = it }
        localOriginIds += origins
        if (alerts.isNotEmpty()) persist()
    }

    fun wipe() = synchronized(this) {
        alerts.clear()
        localOriginIds.clear()
        persist()
    }

    fun remove(id: String) = synchronized(this) {
        if (alerts.remove(id) != null) {
            localOriginIds.remove(id)
            persist()
        }
    }

    private fun load() {
        val decoded = persistence.load()
        alerts.clear()
        decoded.alerts.forEach { alerts[it.id] = it }
        localOriginIds.clear()
        localOriginIds += decoded.localOriginIds
    }

    private fun persist() {
        persistence.save(alerts.values.toList(), localOriginIds)
    }
}
