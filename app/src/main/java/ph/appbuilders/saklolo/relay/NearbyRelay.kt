package ph.appbuilders.saklolo.relay

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import ph.appbuilders.saklolo.audio.WavPcm
import ph.appbuilders.saklolo.contact.DirectGate
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.DirectStore
import ph.appbuilders.saklolo.contact.EndpointCard
import ph.appbuilders.saklolo.contact.ResyncPlan
import ph.appbuilders.saklolo.contact.toDirect
import ph.appbuilders.saklolo.contact.toWire
import ph.appbuilders.saklolo.group.ClipGate
import ph.appbuilders.saklolo.group.GroupNote
import ph.appbuilders.saklolo.group.GroupStore
import ph.appbuilders.saklolo.group.NoteRelay
import ph.appbuilders.saklolo.group.PieceKind
import ph.appbuilders.saklolo.group.RelayPiece
import ph.appbuilders.saklolo.group.SosDispatch
import ph.appbuilders.saklolo.group.toGroupNote
import ph.appbuilders.saklolo.group.toWire
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
    private val groupStore: GroupStore,
    private val directStore: DirectStore,
    private val clipsDir: File,
    private val onAlertsChanged: () -> Unit,
    private val onGroupsChanged: () -> Unit,
    private val onDirectChanged: () -> Unit,
    private val onStatus: (peers: List<NearbyPeer>, message: String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val client: ConnectionsClient = Nearby.getConnectionsClient(appContext)
    private val endpoints = RelayEndpoints()
    private val clipLock = Any()
    private val incomingPayloads = HashMap<Long, Payload>()
    private val payloadToAlert = HashMap<Long, String>()
    private val completedFiles = HashMap<Long, File>()
    private val outgoingClips = LinkedHashMap<Long, OutClip>()
    private val io = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "saklolo-relay-io") }
    private val queueLock = Any()
    private val liveJobs = ArrayDeque<() -> Unit>()
    private val historyJobs = ArrayDeque<() -> Unit>()
    private var advertisingOk = false
    private var discoveryOk = false
    private var advertisePending = false
    private var discoverPending = false

    init {
        ClipRelay.deletePending(clipsDir)
    }

    fun start(name: String) {
        Log.i(BLINK, "relay start service=$SERVICE_ID")
        when (endpoints.beginSession(name)) {
            RelayEndpoints.SessionStart.UNCHANGED -> {
                if (!advertisingOk) beginAdvertising()
                if (!discoveryOk) beginDiscovery()
                if (advertisingOk && discoveryOk) publish("Looking for nearby B-LINK phones")
            }
            RelayEndpoints.SessionStart.RENAME -> {
                advertisingOk = false
                advertisePending = false
                client.stopAdvertising()
                beginAdvertising()
            }
            RelayEndpoints.SessionStart.FRESH -> {
                advertisingOk = false
                discoveryOk = false
                advertisePending = false
                discoverPending = false
                beginAdvertising()
                beginDiscovery()
                publish("Looking for nearby B-LINK phones")
            }
        }
    }

    /** Required setup dropped. The next [start] is a fresh advertise and discovery. */
    fun stopScanning(reason: String) {
        Log.i(BLINK, "relay stopped reason=$reason")
        advertisingOk = false
        discoveryOk = false
        advertisePending = false
        discoverPending = false
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        endpoints.noteStartFailed()
        publish(reason)
    }

    fun setFilter(filter: PeerFilter) {
        endpoints.setFilter(filter)
    }

    /** Foreground start failed before or during [start]. The next start is a fresh one. */
    fun noteServiceStartFailed(message: String) {
        advertisingOk = false
        discoveryOk = false
        advertisePending = false
        discoverPending = false
        endpoints.noteStartFailed()
        publish(message)
    }

    fun peers(): List<NearbyPeer> = endpoints.snapshot()

    fun broadcast(alert: Alert): Int {
        val count = endpoints.snapshot(null).size
        enqueue(live = true) {
            Log.i(BLINK, "send type=sos id=${alert.id} size=bytes endpoints=$count")
            send(listOf(alert), exceptEndpoint = null, forceClips = true)
        }
        return count
    }

    fun broadcastNote(note: GroupNote): Int = sendNotes(listOf(note), exceptEndpoint = null, forceClips = true)

    fun broadcastDirect(message: DirectMessage): Int {
        val peers = endpoints.snapshot(null)
        enqueue(live = true) {
            val withClip = !message.audioPath.isNullOrBlank()
            Log.i(BLINK, "send type=${message.kind} id=${message.id} endpoints=${peers.size} clips=$withClip")
            for (job in ResyncPlan.live(peers.map { it.endpointId }, message, withClip)) {
                deliverDirect(job.endpointId, job.messages, job.attachClips)
            }
        }
        return peers.size
    }

    /** Live work runs before a history dump already queued for a new peer. */
    private fun enqueue(live: Boolean, job: () -> Unit) {
        synchronized(queueLock) {
            if (live) liveJobs.addLast(job) else historyJobs.addLast(job)
        }
        io.execute { drain() }
    }

    private fun drain() {
        while (true) {
            val job = synchronized(queueLock) {
                liveJobs.removeFirstOrNull() ?: historyJobs.removeFirstOrNull()
            } ?: return
            try {
                job()
            } catch (error: Exception) {
                Log.i(BLINK, "relay job failed ${error.message}")
            }
        }
    }

    private fun beginAdvertising() {
        if (advertisingOk || advertisePending) return
        advertisePending = true
        val advertising = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        client.startAdvertising(endpoints.localName(), SERVICE_ID, connectionCallback, advertising)
            .addOnSuccessListener {
                advertisePending = false
                advertisingOk = true
                Log.i(BLINK, "startAdvertising success code=0 message=ok service=$SERVICE_ID")
            }
            .addOnFailureListener { error ->
                advertisePending = false
                val code = (error as? ApiException)?.statusCode
                if (code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                    advertisingOk = true
                    Log.i(BLINK, "startAdvertising success code=$code message=already advertising service=$SERVICE_ID")
                    return@addOnFailureListener
                }
                advertisingOk = false
                Log.i(BLINK, "startAdvertising failure ${blinkFailure(error)} service=$SERVICE_ID")
                failStart("Relay failed to advertise: ${error.message ?: "Play Services unavailable"}")
            }
    }

    private fun beginDiscovery() {
        if (discoveryOk || discoverPending) return
        discoverPending = true
        val discovery = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        client.startDiscovery(SERVICE_ID, discoveryCallback, discovery)
            .addOnSuccessListener {
                discoverPending = false
                discoveryOk = true
                Log.i(BLINK, "startDiscovery success code=0 message=ok service=$SERVICE_ID")
            }
            .addOnFailureListener { error ->
                discoverPending = false
                val code = (error as? ApiException)?.statusCode
                if (code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                    discoveryOk = true
                    Log.i(BLINK, "startDiscovery success code=$code message=already discovering service=$SERVICE_ID")
                    return@addOnFailureListener
                }
                discoveryOk = false
                Log.i(BLINK, "startDiscovery failure ${blinkFailure(error)} service=$SERVICE_ID")
                failStart("Relay failed to discover: ${error.message ?: "check Bluetooth, Wi-Fi, and Location"}")
            }
    }

    private fun failStart(message: String) {
        advertisingOk = false
        discoveryOk = false
        advertisePending = false
        discoverPending = false
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
        val parked = preemptInFlightClips()
        var delivered = 0
        val resumed = HashSet<String>()
        for (peer in targets) {
            if (deliverAlerts(peer.endpointId, wire)) delivered++
            resumeClips(parked.filter { it.endpointId == peer.endpointId })
            resumed += peer.endpointId
        }
        parked.filter { it.endpointId !in resumed }
            .groupBy { it.endpointId }
            .values
            .forEach { resumeClips(it) }
        return delivered
    }

    /** Cancel FILE clips that are still transferring so the SOS BYTES payload is not queued behind them. */
    private fun preemptInFlightClips(): List<OutClip> {
        val inflight = synchronized(clipLock) { outgoingClips.values.toList() }
        val plan = SosDispatch.plan(
            inflight.map { it.payloadId },
            listOf(RelayPiece(PieceKind.SOS_BYTES, "sos")),
        )
        val cancel = plan.cancelFilePayloadIds.toSet()
        val parked = inflight.filter { it.payloadId in cancel }
        for (id in plan.cancelFilePayloadIds) {
            runCatching { client.cancelPayload(id) }
        }
        synchronized(clipLock) {
            plan.cancelFilePayloadIds.forEach { outgoingClips.remove(it) }
        }
        return parked
    }

    private fun resumeClips(clips: List<OutClip>) {
        for (clip in clips.distinctBy { it.ownerId }) {
            if (!clip.file.exists()) continue
            if (clip.direct) {
                val message = directStore.find(clip.ownerId) ?: continue
                deliverDirect(clip.endpointId, listOf(message.copy(audioPath = clip.file.absolutePath)), attachClips = true)
            } else if (clip.note) {
                val note = groupStore.find(clip.ownerId) ?: continue
                deliverNotes(clip.endpointId, listOf(note.copy(audioPath = clip.file.absolutePath)))
            } else {
                val alert = store.find(clip.ownerId) ?: continue
                deliverAlerts(clip.endpointId, listOf(alert.copy(audioPath = clip.file.absolutePath)))
            }
        }
    }

    private fun deliverAlerts(endpointId: String, wire: List<Alert>): Boolean {
        val chunks = chunk(wire)
        var sent = false
        for (chunkAlerts in chunks) {
            val files = prepareFiles(chunkAlerts.map { it.id to it.audioPath })
            val bytes = AlertJson.encodeEnvelope(chunkAlerts, files.map { ClipLink(it.ownerId, it.payload.id) })
                .toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_PAYLOAD) {
                Log.w(TAG, "skipping oversized alert payload (${bytes.size} bytes)")
                continue
            }
            client.sendPayload(endpointId, Payload.fromBytes(bytes))
            sendFiles(endpointId, files, note = false)
            sent = true
        }
        return sent
    }

    private fun sendNotes(notes: List<GroupNote>, exceptEndpoint: String?, forceClips: Boolean): Int {
        val now = System.currentTimeMillis()
        val prepared = notes.mapNotNull { note ->
            if (note.hops >= RelayPolicy.MAX_HOPS) return@mapNotNull null
            val keep = ClipRelay.includeClip(note.createdAtMillis, now, forceClips) &&
                !note.audioPath.isNullOrBlank()
            if (keep) note else note.copy(audioPath = null)
        }
        val targets = endpoints.snapshot(exceptEndpoint)
        if (targets.isEmpty() || prepared.isEmpty()) return 0
        var delivered = 0
        for (peer in targets) {
            if (deliverNotes(peer.endpointId, prepared)) delivered++
        }
        return delivered
    }

    private fun deliverNotes(endpointId: String, notes: List<GroupNote>): Boolean {
        var sent = false
        for (chunkNotes in NoteRelay.chunks(notes, MAX_PAYLOAD)) {
            if (sendNotePayload(endpointId, chunkNotes)) {
                sent = true
            } else if (chunkNotes.size > 1) {
                for (note in chunkNotes) {
                    if (sendNotePayload(endpointId, listOf(note))) sent = true
                }
            }
        }
        return sent
    }

    private fun sendNotePayload(endpointId: String, notes: List<GroupNote>): Boolean {
        val files = prepareFiles(notes.map { it.id to it.audioPath })
        val bytes = AlertJson.encodeEnvelope(
            emptyList(),
            files.map { ClipLink(it.ownerId, it.payload.id) },
            notes.map { it.toWire() },
        ).toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_PAYLOAD) {
            Log.w(TAG, "skipping oversized group payload (${bytes.size} bytes)")
            return false
        }
        client.sendPayload(endpointId, Payload.fromBytes(bytes))
        sendFiles(endpointId, files, note = true)
        return true
    }

    private fun prepareFiles(owners: List<Pair<String, String?>>): List<ReadyClip> {
        val ready = ArrayList<ReadyClip>()
        for ((ownerId, path) in owners) {
            if (path.isNullOrBlank()) continue
            val file = File(path)
            if (!file.exists() || !ClipGate.acceptLength(file.length())) continue
            val payload = try {
                Payload.fromFile(file)
            } catch (error: Exception) {
                Log.w(TAG, "clip payload failed for $ownerId", error)
                continue
            }
            ready += ReadyClip(ownerId, payload, file)
        }
        return ready
    }

    private fun sendFiles(endpointId: String, files: List<ReadyClip>, note: Boolean, direct: Boolean = false) {
        for (file in files) {
            synchronized(clipLock) {
                outgoingClips[file.payload.id] = OutClip(
                    endpointId,
                    file.payload.id,
                    file.ownerId,
                    file.file,
                    note,
                    direct,
                )
            }
            Log.i(BLINK, "file start id=${file.ownerId} payload=${file.payload.id} to=$endpointId")
            client.sendPayload(endpointId, file.payload)
        }
    }

    private fun deliverDirect(endpointId: String, messages: List<DirectMessage>, attachClips: Boolean): Boolean {
        if (messages.isEmpty()) return false
        var sent = false
        var current = ArrayList<DirectMessage>()
        fun flush(chunk: List<DirectMessage>) {
            if (chunk.isEmpty()) return
            val prepared = chunk.map { if (attachClips) it else it.copy(audioPath = null) }
            val files = if (attachClips) prepareFiles(prepared.map { it.id to it.audioPath }) else emptyList()
            val bytes = AlertJson.encodeEnvelope(
                emptyList(),
                files.map { ClipLink(it.ownerId, it.payload.id) },
                emptyList(),
                prepared.map { it.toWire() },
            ).toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_PAYLOAD) {
                Log.w(BLINK, "skipping oversized direct payload (${bytes.size} bytes)")
                return
            }
            val result = runCatching { client.sendPayload(endpointId, Payload.fromBytes(bytes)) }
            Log.d(
                BLINK,
                "send type=direct ids=${prepared.joinToString(",") { it.id }} to=$endpointId size=${bytes.size} clips=${files.size} result=${result.isSuccess}",
            )
            if (result.isSuccess) {
                if (files.isNotEmpty()) sendFiles(endpointId, files, note = false, direct = true)
                sent = true
            }
        }
        for (message in messages) {
            val candidate = current + message
            val tooBig = AlertJson.encodeEnvelope(emptyList(), emptyList(), emptyList(), candidate.map { it.toWire() })
                .toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD
            if (tooBig && current.isNotEmpty()) {
                flush(current)
                current = arrayListOf(message)
            } else {
                current.add(message)
            }
        }
        flush(current)
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

    private fun syncNearby() {
        val ids = endpoints.snapshot().mapNotNull { EndpointCard.decode(it.name)?.deviceId }.toSet()
        directStore.setNearby(ids)
        onDirectChanged()
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
        val freshNotes = groupStore.ingest(
            packet.notes.map { it.toGroupNote() },
            System.currentTimeMillis(),
        )
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
        val freshDirect = directStore.ingest(packet.direct.map { it.toDirect() }, System.currentTimeMillis())
        Log.d(
            BLINK,
            "receive endpoint=$fromEndpoint type=bytes alerts=${packet.alerts.size} direct=${packet.direct.size} accepted=${freshDirect.size}",
        )
        if (fresh.isNotEmpty() || attachedNow.any { store.find(it) != null }) onAlertsChanged()
        if (freshNotes.isNotEmpty() || attachedNow.any { groupStore.find(it) != null }) onGroupsChanged()
        if (freshDirect.isNotEmpty()) onDirectChanged()
        val forwardDirect = freshDirect.filter { DirectGate.shouldForward(it, directStore.myId) }
        if (forwardDirect.isNotEmpty()) {
            enqueue(live = true) {
                for (peer in endpoints.snapshot(fromEndpoint)) {
                    deliverDirect(peer.endpointId, forwardDirect, attachClips = false)
                }
            }
        }
        sweepPending()
        val pendingForward = fresh.filter { it.id !in attachedNow }
        if (pendingForward.isNotEmpty()) {
            send(pendingForward, exceptEndpoint = fromEndpoint, forceClips = true)
        }
        val pendingNotes = freshNotes.filter { it.id !in attachedNow }
        if (pendingNotes.isNotEmpty()) {
            sendNotes(pendingNotes, exceptEndpoint = fromEndpoint, forceClips = true)
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
        deleteSharedOriginal(uri)
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
            val forAlert = store.find(alertId) != null
            val forNote = groupStore.find(alertId) != null
            if (storeClip(alertId, temp, fromEndpoint)) {
                if (forAlert) onAlertsChanged()
                if (forNote) onGroupsChanged()
            } else if (temp.exists()) {
                synchronized(clipLock) { completedFiles[payloadId] = temp }
            }
        }
        sweepPending()
    }

    /** Drops the Nearby download once the bytes are in app storage. A locked URI is left alone. */
    private fun deleteSharedOriginal(uri: android.net.Uri) {
        try {
            appContext.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
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

    private fun storeClip(ownerId: String, source: File, fromEndpoint: String?): Boolean {
        if (!source.exists()) return false
        if (!ClipGate.acceptLength(source.length())) {
            discardPending(source)
            return false
        }
        if (store.find(ownerId) != null) return storeAlertClip(ownerId, source, fromEndpoint)
        if (directStore.find(ownerId) != null) return storeDirectClip(ownerId, source, fromEndpoint)
        if (groupStore.find(ownerId) != null) return storeNoteClip(ownerId, source, fromEndpoint)
        return false
    }

    private fun storeAlertClip(alertId: String, source: File, fromEndpoint: String?): Boolean {
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

    private fun storeDirectClip(messageId: String, source: File, fromEndpoint: String?): Boolean {
        val current = directStore.find(messageId) ?: return false
        val existing = current.audioPath
        if (existing != null && File(existing).let { it.exists() && it.length() > WavPcm.HEADER_BYTES }) {
            discardPending(source)
            return false
        }
        val dest = WavPcm.clipFile(clipsDir, messageId)
        if (!copyClip(source, dest)) return false
        discardPending(source)
        directStore.attachAudio(messageId, dest.absolutePath)
        onDirectChanged()
        Log.i(BLINK, "file stored id=$messageId")
        val updated = directStore.find(messageId) ?: return true
        if (DirectGate.shouldForward(updated, directStore.myId)) {
            enqueue(live = true) {
                for (peer in endpoints.snapshot(fromEndpoint)) {
                    deliverDirect(peer.endpointId, listOf(updated), attachClips = true)
                }
            }
        }
        return true
    }

    private fun storeNoteClip(noteId: String, source: File, fromEndpoint: String?): Boolean {
        val current = groupStore.find(noteId) ?: return false
        val existing = current.audioPath
        if (existing != null && File(existing).let { it.exists() && it.length() > WavPcm.HEADER_BYTES }) {
            discardPending(source)
            return false
        }
        val dest = WavPcm.clipFile(clipsDir, noteId)
        if (!copyClip(source, dest)) return false
        discardPending(source)
        groupStore.attachAudio(noteId, dest.absolutePath)
        val updated = groupStore.find(noteId) ?: return true
        sendNotes(listOf(updated), exceptEndpoint = fromEndpoint, forceClips = true)
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
            ClipGate.acceptLength(dest.length())
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
            val status = update.status
            if (
                status == PayloadTransferUpdate.Status.SUCCESS ||
                status == PayloadTransferUpdate.Status.FAILURE ||
                status == PayloadTransferUpdate.Status.CANCELED
            ) {
                Log.i(BLINK, "file progress payload=${update.payloadId} status=$status")
                synchronized(clipLock) { outgoingClips.remove(update.payloadId) }
            }
            if (status != PayloadTransferUpdate.Status.SUCCESS) return
            val payloadId = update.payloadId
            io.execute { handleFileSuccess(payloadId, endpointId) }
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val name = info.endpointName.orEmpty()
            Log.i(BLINK, "connection initiated id=$endpointId")
            endpoints.rememberName(endpointId, name)
            if (!endpoints.allows(name)) {
                client.rejectConnection(endpointId)
                endpoints.markConnectFailed(endpointId)
                return
            }
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val code = result.status.statusCode
            val message = result.status.statusMessage ?: ""
            Log.i(BLINK, "connection result id=$endpointId code=$code message=$message")
            if (result.status.isSuccess) {
                endpoints.markConnected(endpointId, "Nearby phone", System.currentTimeMillis())
                val peerName = endpoints.snapshot().firstOrNull { it.endpointId == endpointId }?.name
                val card = EndpointCard.decode(peerName)
                Log.i(BLINK, "join endpoint=$endpointId parsed=${card != null}")
                if (card != null) {
                    directStore.notePeer(card.deviceId, card.name, System.currentTimeMillis())
                }
                syncNearby()
                publish("Connected to a nearby phone")
                enqueue(live = false) {
                    val plan = ResyncPlan.history(endpointId, directStore.relayHistory())
                    val alerts = store.snapshot().map { it.copy(audioPath = null) }
                    Log.i(BLINK, "resync endpoint=$endpointId alerts=${alerts.size} direct=${plan.messages.size} clips=false")
                    if (alerts.isNotEmpty()) deliverAlerts(endpointId, alerts)
                    if (plan.messages.isNotEmpty()) deliverDirect(endpointId, plan.messages, attachClips = false)
                }
            } else {
                endpoints.markConnectFailed(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i(BLINK, "disconnected id=$endpointId")
            endpoints.markDisconnected(endpointId)
            syncNearby()
            val peers = endpoints.snapshot()
            publish(if (peers.isEmpty()) "Looking for nearby B-LINK phones" else "A phone disconnected")
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val name = info.endpointName.orEmpty()
            Log.i(BLINK, "endpoint found id=$endpointId")
            if (!endpoints.tryBeginConnect(endpointId, name)) return
            client.requestConnection(endpoints.localName(), endpointId, connectionCallback)
                .addOnFailureListener {
                    endpoints.markConnectFailed(endpointId)
                    Log.i(BLINK, "connection initiated failure id=$endpointId ${blinkFailure(it)}")
                }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.i(BLINK, "endpoint lost id=$endpointId")
            endpoints.onLost(endpointId)
        }
    }

    companion object {
        private const val TAG = "SakloloRelay"
        private const val BLINK = "BLINK"
        const val SERVICE_ID = "ph.appbuilders.saklolo.relay"
        private const val MAX_PAYLOAD = NoteRelay.MAX_BYTES
    }
}

internal fun blinkFailure(error: Exception): String {
    val code = (error as? ApiException)?.statusCode ?: -1
    val message = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    return "code=$code message=$message"
}

private data class OutClip(
    val endpointId: String,
    val payloadId: Long,
    val ownerId: String,
    val file: File,
    val note: Boolean,
    val direct: Boolean = false,
)

private data class ReadyClip(
    val ownerId: String,
    val payload: Payload,
    val file: File,
)
