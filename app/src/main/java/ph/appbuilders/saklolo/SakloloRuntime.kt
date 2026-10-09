package ph.appbuilders.saklolo

import android.app.Application
import android.content.Intent
import androidx.room.Room
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ph.appbuilders.saklolo.contact.ContactRow
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.DirectStore
import ph.appbuilders.saklolo.data.MIGRATION_1_2
import ph.appbuilders.saklolo.data.MIGRATION_2_3
import ph.appbuilders.saklolo.data.MIGRATION_3_4
import ph.appbuilders.saklolo.data.MIGRATION_4_5
import ph.appbuilders.saklolo.data.MIGRATION_5_6
import ph.appbuilders.saklolo.data.MIGRATION_6_7
import ph.appbuilders.saklolo.data.RoomAlertPersistence
import ph.appbuilders.saklolo.data.RoomDirectPersistence
import ph.appbuilders.saklolo.data.RoomGroupPersistence
import ph.appbuilders.saklolo.data.SakloloDatabase
import ph.appbuilders.saklolo.group.ConcertGroup
import ph.appbuilders.saklolo.group.GroupNote
import ph.appbuilders.saklolo.group.GroupStore
import ph.appbuilders.saklolo.group.Sighting
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.model.AlertJson
import ph.appbuilders.saklolo.model.AlertStore
import ph.appbuilders.saklolo.relay.NearbyPeer
import ph.appbuilders.saklolo.relay.NearbyRelay
import ph.appbuilders.saklolo.relay.RelayService
import ph.appbuilders.saklolo.relay.PeerFilter
import ph.appbuilders.saklolo.relay.ReadyToConnect
import ph.appbuilders.saklolo.relay.SetupProbe
import ph.appbuilders.saklolo.summary.GemmaSummarizer

/**
 * Process-wide alert log and Nearby relay. The foreground service and the
 * screen share this object so leaving the activity does not stop the mesh.
 */
class SakloloRuntime private constructor(val app: Application) {
    val settings = DemoSettings(app)
    val clipsDir: File = File(app.filesDir, "clips").also { it.mkdirs() }
    val store: AlertStore
    val groupStore: GroupStore
    val directStore: DirectStore
    val relay: NearbyRelay

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private val _peers = MutableStateFlow<List<NearbyPeer>>(emptyList())
    val peers: StateFlow<List<NearbyPeer>> = _peers.asStateFlow()

    private val _relayMessage = MutableStateFlow("Looking for nearby B-LINK phones")
    val relayMessage: StateFlow<String> = _relayMessage.asStateFlow()

    private val _groups = MutableStateFlow<List<ConcertGroup>>(emptyList())
    val groups: StateFlow<List<ConcertGroup>> = _groups.asStateFlow()

    private val _groupNotes = MutableStateFlow<List<GroupNote>>(emptyList())
    val groupNotes: StateFlow<List<GroupNote>> = _groupNotes.asStateFlow()

    private val _sightings = MutableStateFlow<List<Sighting>>(emptyList())
    val sightings: StateFlow<List<Sighting>> = _sightings.asStateFlow()

    private val _activeGroup = MutableStateFlow<ConcertGroup?>(null)
    val activeGroup: StateFlow<ConcertGroup?> = _activeGroup.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactRow>>(emptyList())
    val contacts: StateFlow<List<ContactRow>> = _contacts.asStateFlow()

    private val _directMessages = MutableStateFlow<List<DirectMessage>>(emptyList())
    val directMessages: StateFlow<List<DirectMessage>> = _directMessages.asStateFlow()

    init {
        app.getExternalFilesDir(null)?.mkdirs()
        val database = Room.databaseBuilder(app, SakloloDatabase::class.java, "saklolo.db")
            .allowMainThreadQueries()
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .fallbackToDestructiveMigration()
            .build()
        store = AlertStore(RoomAlertPersistence(database))
        groupStore = GroupStore(RoomGroupPersistence(database))
        directStore = DirectStore(RoomDirectPersistence(database))
        directStore.myId = settings.deviceId
        importLegacyFile()
        _alerts.value = store.snapshot()
        refreshGroups()
        refreshDirect()
        relay = NearbyRelay(
            context = app,
            store = store,
            groupStore = groupStore,
            directStore = directStore,
            clipsDir = clipsDir,
            onAlertsChanged = { refreshAlerts() },
            onGroupsChanged = { refreshGroups() },
            onDirectChanged = { refreshDirect() },
            onStatus = { peers, message ->
                _peers.value = peers
                _relayMessage.value = message
            },
        )
        relay.setFilter(settings.peerFilter())
        GemmaSummarizer.preload(app)
    }

    fun ensureRelay() {
        val facts = SetupProbe.read(app)
        val line = ReadyToConnect.permissionLine(facts)
        relay.setFilter(settings.peerFilter())
        if (!settings.termsAccepted) {
            android.util.Log.i("BLINK", "relay service start skipped until terms")
            relay.stopScanning("Setup needed")
            return
        }
        if (!ReadyToConnect.shouldStart(facts)) {
            android.util.Log.i("BLINK", "relay service start skipped permissions=$line")
            relay.stopScanning("Setup needed (${ReadyToConnect.requiredMissing(facts)})")
            return
        }
        android.util.Log.i("BLINK", "relay service start permissions=$line")
        relay.start(settings.endpointName())
    }

    fun noteRelayStartFailed(message: String) {
        relay.noteServiceStartFailed(message)
    }

    fun wipeUserData(clearReplies: () -> Unit = {}) {
        UserDataErase.run(
            stopRelay = {
                relay.stopScanning("Deleted")
                app.stopService(Intent(app, RelayService::class.java))
            },
            clearRoom = {
                directStore.wipe()
                store.wipe()
                groupStore.wipe()
            },
            clearClips = {
                clipsDir.listFiles()?.forEach { file -> file.delete() }
                File(app.filesDir, "alerts.json").delete()
            },
            clearPrefs = { settings.clear() },
            clearReplies = clearReplies,
            newDeviceId = {
                directStore.myId = settings.deviceId
                directStore.myId
            },
        )
        refreshAlerts()
        refreshGroups()
        refreshDirect()
    }

    fun applyDemo(name: String, restrict: Boolean, allowlist: String) {
        settings.deviceName = name
        settings.restrictPeers = restrict
        settings.allowlistRaw = allowlist
        relay.setFilter(PeerFilter(restrict, ph.appbuilders.saklolo.relay.parseAllowlist(allowlist)))
        ensureRelay()
    }

    fun refreshAlerts() {
        _alerts.value = store.snapshot()
    }

    fun refreshDirect() {
        _contacts.value = directStore.rows()
        _directMessages.value = directStore.visible(settings.deviceId)
    }

    fun refreshGroups() {
        _groups.value = groupStore.groups()
        _groupNotes.value = groupStore.visible()
        _sightings.value = groupStore.allSightings()
        _activeGroup.value = groupStore.active()
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
