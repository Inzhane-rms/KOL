package ph.appbuilders.saklolo.model

import ph.appbuilders.saklolo.relay.RelayPolicy
import java.io.File

/**
 * On-disk alert log. Dedupes by id and refuses anything past the hop limit.
 * Local origin ids stay on this phone so rebroadcasts can increment hops.
 */
class AlertStore(private val file: File) {
    private val alerts = linkedMapOf<String, Alert>()
    private val localOriginIds = mutableSetOf<String>()

    init {
        load()
    }

    fun snapshot(): List<Alert> = synchronized(this) {
        alerts.values.toList().sortedForFeed()
    }

    fun isLocalOrigin(id: String): Boolean = synchronized(this) { id in localOriginIds }

    fun addLocal(alert: Alert) = synchronized(this) {
        alerts[alert.id] = alert
        localOriginIds += alert.id
        persist()
    }

    fun ingest(incoming: List<Alert>): List<Alert> = synchronized(this) {
        val fresh = mutableListOf<Alert>()
        for (alert in incoming) {
            if (!RelayPolicy.shouldStore(alert, alerts.keys)) continue
            alerts[alert.id] = alert
            fresh += alert
        }
        if (fresh.isNotEmpty()) persist()
        fresh
    }

    fun remove(id: String) = synchronized(this) {
        if (alerts.remove(id) != null) {
            localOriginIds.remove(id)
            persist()
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val decoded = AlertJson.decodeFile(file.readText())
            alerts.clear()
            decoded.alerts.forEach { alerts[it.id] = it }
            localOriginIds.clear()
            localOriginIds += decoded.localOriginIds
        } catch (_: Exception) {
            alerts.clear()
            localOriginIds.clear()
        }
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        file.writeText(AlertJson.encodeFile(alerts.values.toList(), localOriginIds))
    }
}
