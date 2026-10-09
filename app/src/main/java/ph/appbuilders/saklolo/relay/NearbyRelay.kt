package ph.appbuilders.saklolo.relay

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import ph.appbuilders.saklolo.audio.WavPcm
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.model.AlertJson
import ph.appbuilders.saklolo.model.AlertStore
import ph.appbuilders.saklolo.model.ClipLink
import java.io.File
import java.util.concurrent.Executors

/**
 * Phone-to-phone relay over Google Nearby Connections, strategy P2P_CLUSTER.
 * Works with Wi-Fi and Bluetooth radios on, including airplane mode with no
 * access point and no internet.
 *
 * Alert bytes and the original 16 kHz voice clip (a Nearby FILE payload) travel
 * together. The clip is linked to the alert id. Endpoint sets are only touched
 * through [RelayEndpoints], which is also what [send] locks on.
 */
class NearbyRelay(
    context: Context,
    private val store: AlertStore,
    private val clipsDir: File,
    private val onAlertsChanged: () -> Unit,
    private val onStatus: (peers: List<NearbyPeer>, message: String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val client: ConnectionsClient = Nearby.getConnectionsClient(appContext)
    private val endpoints = RelayEndpoints()
    private val clipLock = Any()
    private val incomingPayloads = HashMap<Long, Payload>()
    private val payloadToAlert = HashMap<Long, String>()
    private val completedFiles = HashMap<Long, File>()
    private val io = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "saklolo-relay-io") }

    init {
        ClipRelay.deletePending(clipsDir)
    }

    fun start(name: String) {
        when (endpoints.beginSession(name)) {
            RelayEndpoints.SessionStart.UNCHANGED -> publish("Relaying SOS alerts nearby")
            RelayEndpoints.SessionStart.RENAME -> {
                client.stopAdvertising()
                beginAdvertising()
            }
            RelayEndpoints.SessionStart.FRESH -> {
                beginAdvertising()
                beginDiscovery()
                publish("Looking for nearby B-LINK phones")
            }
        }
    }

    fun setFilter(filter: PeerFilter) {
        endpoints.setFilter(filter)
    }

    fun peers(): List<NearbyPeer> = endpoints.snapshot()

    fun broadcast(alert: Alert): Int = send(listOf(alert), exceptEndpoint = null, forceClips = true)

    private fun beginAdvertising() {
        val advertising = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        client.startAdvertising(endpoints.localName(), SERVICE_ID, connectionCallback, advertising)
            .addOnFailureListener { error ->
                Log.w(TAG, "advertise failed", error)
                failStart("Relay failed to advertise: ${error.message ?: "Play Services unavailable"}")
            }
    }

    private fun beginDiscovery() {
        val discovery = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        client.startDiscovery(SERVICE_ID, discoveryCallback, discovery)
            .addOnFailureListener { error ->
                Log.w(TAG, "discovery failed", error)
                failStart("Relay failed to discover: ${error.message ?: "check Bluetooth and Wi-Fi"}")
            }
    }

    private fun failStart(message: String) {
        endpoints.noteStartFailed()
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        publish(message)
    }

    private fun send(alerts: List<Alert>, exceptEndpoint: String?, forceClips: Boolean): Int {
        val now = System.currentTimeMillis()
        val prepared = alerts.map { alert ->
            val keepClip = ClipRelay.includeClip(alert.createdAtMillis, now, forceClips) &&
                !alert.audioPath.isNullOrBlank()
            if (keepClip) alert else alert.copy(audioPath = null)
        }
        val targets = endpoints.snapshot(exceptEndpoint)
        if (targets.isEmpty()) return 0
        val wire = prepared.mapNotNull { RelayPolicy.outgoing(it) }
        if (wire.isEmpty()) return 0
        var delivered = 0
        for (peer in targets) {
            if (sendOne(peer.endpointId, wire)) delivered++
        }
        return delivered
    }

    private fun sendOne(endpointId: String, wire: List<Alert>): Boolean {
        val chunks = chunk(wire)
        var sent = false
        for (chunkAlerts in chunks) {
            val clips = ArrayList<ClipLink>()
            val files = ArrayList<Payload>()
            for (alert in chunkAlerts) {
                val path = alert.audioPath ?: continue
                val file = File(path)
                if (!file.exists() || file.length() !in 45..MAX_CLIP_BYTES) continue
                val payload = try {
                    Payload.fromFile(file)
                } catch (error: Exception) {
                    Log.w(TAG, "clip payload failed for ${alert.id}", error)
                    continue
                }
                clips += ClipLink(alert.id, payload.id)
                files += payload
            }
            val bytes = AlertJson.encodeEnvelope(chunkAlerts, clips).toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_PAYLOAD) {
                Log.w(TAG, "skipping oversized alert payload (${bytes.size} bytes)")
                continue
            }
            client.sendPayload(endpointId, Payload.fromBytes(bytes))
            files.forEach { client.sendPayload(endpointId, it) }
            sent = true
        }
        return sent
    }

    private fun chunk(wire: List<Alert>): List<List<Alert>> {
        val chunks = ArrayList<List<Alert>>()
        var current = ArrayList<Alert>()
        for (alert in wire) {
            val candidate = current + alert
            val tooBig = AlertJson.encodeEnvelope(candidate).toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD
            if (tooBig && current.isNotEmpty()) {
                chunks += current
                current = arrayListOf(alert)
            } else {
                current.add(alert)
            }
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    private fun publish(message: String) {
        onStatus(endpoints.snapshot(), message)
    }

    private fun handleBytes(fromEndpoint: String, payload: Payload) {
        val bytes = payload.asBytes() ?: return
        val packet = try {
            AlertJson.decodeEnvelope(bytes.toString(Charsets.UTF_8))
        } catch (error: Exception) {
            Log.w(TAG, "bad payload", error)
            return
        }
        val fresh = store.ingest(packet.alerts)
        val attachedNow = HashSet<String>()
        for (link in packet.clips) {
            val ready = synchronized(clipLock) {
                payloadToAlert[link.payloadId] = link.alertId
                completedFiles.remove(link.payloadId)
            }
            if (ready != null && storeClip(link.alertId, ready, fromEndpoint)) {
                attachedNow += link.alertId
            }
        }
        if (fresh.isNotEmpty() || attachedNow.isNotEmpty()) onAlertsChanged()
        sweepPending()
        val pendingForward = fresh.filter { it.id !in attachedNow }
        if (pendingForward.isNotEmpty()) {
            send(pendingForward, exceptEndpoint = fromEndpoint, forceClips = true)
        }
        if (fresh.isNotEmpty()) {
            publish("Received ${fresh.size} alert${if (fresh.size == 1) "" else "s"}")
        }
    }

    private fun handleFileSuccess(payloadId: Long, fromEndpoint: String) {
        val payload = synchronized(clipLock) { incomingPayloads.remove(payloadId) } ?: return
        if (payload.type != Payload.Type.FILE) return
        val shared = payload.asFile()
        val uri = shared?.asUri()
        if (uri == null) {
            Log.w(TAG, "clip has no content uri for $payloadId")
            return
        }
        val temp = File(clipsDir, "pending-$payloadId.wav")
        val copied = try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                ClipRelay.copyStream(input, temp)
            } ?: false
        } catch (error: Exception) {
            Log.w(TAG, "clip copy failed for $payloadId", error)
            temp.delete()
            false
        }
        if (!copied) {
            Log.w(TAG, "could not read clip $payloadId into app storage")
            temp.delete()
            return
        }
        deleteSharedOriginal(shared)
        val alertId = synchronized(clipLock) {
            val known = payloadToAlert[payloadId]
            if (known == null) {
                completedFiles[payloadId] = temp
                null
            } else {
                known
            }
        }
        if (alertId != null) {
            if (storeClip(alertId, temp, fromEndpoint)) onAlertsChanged()
            else if (temp.exists()) {
                synchronized(clipLock) { completedFiles[payloadId] = temp }
            }
        }
        sweepPending()
    }

    /** Drops the Nearby download once the bytes are in app storage. Skips it when the file is locked. */
    @Suppress("DEPRECATION")
    private fun deleteSharedOriginal(shared: Payload.File) {
        val javaFile = try {
            shared.asJavaFile()
        } catch (error: Throwable) {
            Log.w(TAG, "shared clip is not accessible", error)
            null
        }
        if (javaFile == null || !javaFile.exists()) return
        if (!javaFile.delete()) {
            Log.w(TAG, "could not delete shared clip ${javaFile.name}")
        }
    }

    /** Incoming voice transfer finished, or the local recording stopped. */
    fun onLocalRecordingFinished() {
        io.execute { sweepPending() }
    }

    private fun sweepPending() {
        val keep = synchronized(clipLock) { completedFiles.values.map { it.name }.toSet() }
        ClipRelay.deletePending(clipsDir, keep)
    }

    private fun storeClip(alertId: String, source: File, fromEndpoint: String?): Boolean {
        if (!source.exists()) return false
        if (source.length() !in 45..MAX_CLIP_BYTES) {
            discardPending(source)
            return false
        }
        val current = store.find(alertId) ?: return false
        val existing = current.audioPath
        if (existing != null && File(existing).let { it.exists() && it.length() > WavPcm.HEADER_BYTES }) {
            discardPending(source)
            return false
        }
        val dest = WavPcm.clipFile(clipsDir, alertId)
        if (!copyClip(source, dest)) return false
        discardPending(source)
        store.attachAudio(alertId, dest.absolutePath)
        val updated = store.find(alertId) ?: return true
        send(listOf(updated), exceptEndpoint = fromEndpoint, forceClips = true)
        return true
    }

    private fun discardPending(source: File) {
        if (!ClipRelay.isPendingWav(source)) return
        if (!source.delete()) Log.w(TAG, "could not delete ${source.name}")
    }

    private fun copyClip(source: File, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            source.copyTo(dest, overwrite = true)
            dest.length() in 45..MAX_CLIP_BYTES
        } catch (error: Exception) {
            Log.w(TAG, "clip copy failed", error)
            false
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> io.execute { handleBytes(endpointId, payload) }
                Payload.Type.FILE -> io.execute {
                    synchronized(clipLock) { incomingPayloads[payload.id] = payload }
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status != PayloadTransferUpdate.Status.SUCCESS) return
            val payloadId = update.payloadId
            io.execute { handleFileSuccess(payloadId, endpointId) }
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val name = info.endpointName.orEmpty()
            endpoints.rememberName(endpointId, name)
            if (!endpoints.allows(name)) {
                client.rejectConnection(endpointId)
                endpoints.markConnectFailed(endpointId)
                return
            }
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                endpoints.markConnected(endpointId, "Nearby phone", System.currentTimeMillis())
                publish("Connected to a nearby phone")
                send(store.snapshot(), exceptEndpoint = null, forceClips = false)
            } else {
                endpoints.markConnectFailed(endpointId)
                Log.w(TAG, "connection failed ${result.status}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            endpoints.markDisconnected(endpointId)
            val peers = endpoints.snapshot()
            publish(if (peers.isEmpty()) "Looking for nearby B-LINK phones" else "A phone disconnected")
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val name = info.endpointName.orEmpty()
            if (!endpoints.tryBeginConnect(endpointId, name)) return
            client.requestConnection(endpoints.localName(), endpointId, connectionCallback)
                .addOnFailureListener {
                    endpoints.markConnectFailed(endpointId)
                    Log.w(TAG, "requestConnection failed", it)
                }
        }

        override fun onEndpointLost(endpointId: String) {
            endpoints.onLost(endpointId)
        }
    }

    companion object {
        private const val TAG = "SakloloRelay"
        const val SERVICE_ID = "ph.appbuilders.saklolo.relay"
        private const val MAX_PAYLOAD = 32 * 1024
        private const val MAX_CLIP_BYTES = 1_000_000
    }
}
