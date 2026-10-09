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
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.model.AlertJson
import ph.appbuilders.saklolo.model.AlertStore

/**
 * Phone-to-phone relay over Google Nearby Connections, strategy P2P_CLUSTER.
 * Works with Wi-Fi and Bluetooth radios on, including airplane mode with no
 * access point and no internet. Each phone rebroadcasts alerts it has not
 * seen, up to the hop limit.
 */
class NearbyRelay(
    context: Context,
    private val store: AlertStore,
    private val onAlertsChanged: () -> Unit,
    private val onStatus: (peers: Int, message: String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val client: ConnectionsClient = Nearby.getConnectionsClient(appContext)
    private val connected = linkedSetOf<String>()
    private val pending = linkedSetOf<String>()
    private var running = false
    private var localName = "Saklolo"

    fun start(name: String) {
        if (running) return
        localName = name
        running = true
        publishStatus("Looking for nearby Saklolo phones")
        val advertising = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        client.startAdvertising(localName, SERVICE_ID, connectionCallback, advertising)
            .addOnFailureListener { error ->
                Log.w(TAG, "advertise failed", error)
                publishStatus("Relay failed to advertise: ${error.message ?: "Play Services unavailable"}")
            }
        val discovery = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        client.startDiscovery(SERVICE_ID, discoveryCallback, discovery)
            .addOnFailureListener { error ->
                Log.w(TAG, "discovery failed", error)
                publishStatus("Relay failed to discover: ${error.message ?: "check Bluetooth and Wi-Fi"}")
            }
    }

    fun stop() {
        running = false
        client.stopAllEndpoints()
        client.stopAdvertising()
        client.stopDiscovery()
        connected.clear()
        pending.clear()
        publishStatus("Relay off")
    }

    fun broadcast(alert: Alert) {
        send(listOf(alert), exceptEndpoint = null)
    }

    private fun send(alerts: List<Alert>, exceptEndpoint: String?) {
        val endpoints = synchronized(connected) { connected.filter { it != exceptEndpoint } }
        if (endpoints.isEmpty()) return
        val wire = alerts.mapNotNull { alert ->
            RelayPolicy.outgoing(alert, store.isLocalOrigin(alert.id))
        }
        if (wire.isEmpty()) return
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
        for (chunk in chunks) {
            val bytes = AlertJson.encodeEnvelope(chunk).toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_PAYLOAD) {
                Log.w(TAG, "skipping oversized alert payload (${bytes.size} bytes)")
                continue
            }
            endpoints.forEach { endpoint ->
                client.sendPayload(endpoint, Payload.fromBytes(bytes))
            }
        }
    }

    private fun publishStatus(message: String) {
        onStatus(connected.size, message)
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val incoming = try {
                AlertJson.decodeEnvelope(bytes.toString(Charsets.UTF_8))
            } catch (error: Exception) {
                Log.w(TAG, "bad payload", error)
                return
            }
            val fresh = store.ingest(incoming)
            if (fresh.isEmpty()) return
            onAlertsChanged()
            send(fresh, exceptEndpoint = endpointId)
            publishStatus("Received ${fresh.size} alert${if (fresh.size == 1) "" else "s"}")
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pending.remove(endpointId)
            if (result.status.isSuccess) {
                connected.add(endpointId)
                publishStatus("Connected to a nearby phone")
                send(store.snapshot(), exceptEndpoint = null)
            } else {
                Log.w(TAG, "connection failed ${result.status}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            pending.remove(endpointId)
            publishStatus(if (connected.isEmpty()) "Looking for nearby Saklolo phones" else "A phone disconnected")
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (!running || endpointId in connected || endpointId in pending) return
            pending.add(endpointId)
            client.requestConnection(localName, endpointId, connectionCallback)
                .addOnFailureListener {
                    pending.remove(endpointId)
                    Log.w(TAG, "requestConnection failed", it)
                }
        }

        override fun onEndpointLost(endpointId: String) {
            pending.remove(endpointId)
        }
    }

    companion object {
        private const val TAG = "SakloloRelay"
        private const val SERVICE_ID = "ph.appbuilders.saklolo.relay"
        private const val MAX_PAYLOAD = 32 * 1024
    }
}
