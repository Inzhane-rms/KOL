package ph.appbuilders.saklolo

import android.app.Application
import androidx.room.Room
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ph.appbuilders.saklolo.data.RoomAlertPersistence
import ph.appbuilders.saklolo.data.SakloloDatabase
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.model.AlertJson
import ph.appbuilders.saklolo.model.AlertStore
import ph.appbuilders.saklolo.relay.NearbyPeer
import ph.appbuilders.saklolo.relay.NearbyRelay
import ph.appbuilders.saklolo.relay.PeerFilter

/**
 * Process-wide alert log and Nearby relay. The foreground service and the
 * screen share this object so leaving the activity does not stop the mesh.
 */
class SakloloRuntime private constructor(val app: Application) {
    val settings = DemoSettings(app)
    val clipsDir: File = File(app.filesDir, "clips").also { it.mkdirs() }
    val store: AlertStore
    val relay: NearbyRelay

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private val _peers = MutableStateFlow<List<NearbyPeer>>(emptyList())
    val peers: StateFlow<List<NearbyPeer>> = _peers.asStateFlow()

    private val _relayMessage = MutableStateFlow("Looking for nearby B-LINK phones")
    val relayMessage: StateFlow<String> = _relayMessage.asStateFlow()

    init {
        app.getExternalFilesDir(null)?.mkdirs()
        val database = Room.databaseBuilder(app, SakloloDatabase::class.java, "saklolo.db")
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .build()
        store = AlertStore(RoomAlertPersistence(database))
        importLegacyFile()
        _alerts.value = store.snapshot()
        relay = NearbyRelay(
            context = app,
            store = store,
            clipsDir = clipsDir,
            onAlertsChanged = { refreshAlerts() },
            onStatus = { peers, message ->
                _peers.value = peers
                _relayMessage.value = message
            },
        )
        relay.setFilter(settings.peerFilter())
    }

    fun ensureRelay() {
        relay.setFilter(settings.peerFilter())
        relay.start(settings.deviceName)
    }

    fun applyDemo(name: String, restrict: Boolean, allowlist: String) {
        settings.deviceName = name
        settings.restrictPeers = restrict
        settings.allowlistRaw = allowlist
        relay.setFilter(PeerFilter(restrict, ph.appbuilders.saklolo.relay.parseAllowlist(allowlist)))
        relay.start(settings.deviceName)
    }

    fun refreshAlerts() {
        _alerts.value = store.snapshot()
    }

    private fun importLegacyFile() {
        if (store.snapshot().isNotEmpty()) return
        val legacy = File(app.filesDir, "alerts.json")
        if (!legacy.exists()) return
        val decoded = try {
            AlertJson.decodeFile(legacy.readText())
        } catch (_: Exception) {
            return
        }
        store.importExisting(decoded.alerts, decoded.localOriginIds.toSet())
    }

    companion object {
        @Volatile
        private var instance: SakloloRuntime? = null

        fun get(app: Application): SakloloRuntime =
            instance ?: synchronized(this) {
                instance ?: SakloloRuntime(app.applicationContext as Application).also { instance = it }
            }
    }
}
