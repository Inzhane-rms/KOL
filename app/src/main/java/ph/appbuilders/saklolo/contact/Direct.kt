package ph.appbuilders.saklolo.contact

import java.net.URLDecoder
import ph.appbuilders.saklolo.model.WireDirect
import ph.appbuilders.saklolo.relay.RelayPolicy
import ph.appbuilders.saklolo.triage.TriageEngine
import ph.appbuilders.saklolo.triage.Urgency

data class SavedContact(
    val deviceId: String,
    val name: String,
    val addedAtMillis: Long,
    val favorite: Boolean,
    val lastHeardMillis: Long,
    val saved: Boolean,
    val publicKey: String = "",
)

data class ContactRow(
    val deviceId: String,
    val name: String,
    val favorite: Boolean,
    val saved: Boolean,
    val inRange: Boolean,
    val lastHeardMillis: Long,
    val addedAtMillis: Long = 0L,
)

data class DirectMessage(
    val id: String,
    val fromDeviceId: String,
    val toDeviceId: String,
    val senderName: String,
    val body: String,
    val createdAtMillis: Long,
    val hops: Int = 0,
    val kind: String = "text",
    val audioPath: String? = null,
    val localOrigin: Boolean = false,
    val box: String = "",
    val delivery: String = Delivery.SENDING,
    val relayHops: Int = 0,
    val clipBytes: Int = 0,
    val expireAtMillis: Long = 0L,
    val clipPath: String? = null,
)

data class DirectSnapshot(
    val contacts: List<SavedContact> = emptyList(),
    val messages: List<DirectMessage> = emptyList(),
)

data class Conversation(
    val peerId: String,
    val name: String,
    val snippet: String,
    val atMillis: Long,
    val unread: Int,
    val critical: Boolean,
    val criticalBody: String,
)

interface DirectPersistence {
    fun load(): DirectSnapshot
    fun save(snapshot: DirectSnapshot)

    /** Room writes hop to a background executor. Memory saves stay inline so tests stay ordered. */
    val async: Boolean get() = false
}

class MemoryDirectPersistence : DirectPersistence {
    var snapshot: DirectSnapshot = DirectSnapshot()
    override fun load(): DirectSnapshot = snapshot
    override fun save(snapshot: DirectSnapshot) {
        this.snapshot = snapshot
    }
}

data class ParsedContact(val deviceId: String, val name: String, val publicKey: String = "")

/** Personal QR. Version 2 carries the X25519 public key. Group codes are rejected. */
object ContactQr {
    const val PREFIX = "BLKC"

    fun encode(deviceId: String, name: String, publicKey: String? = null): String {
        val safeName = name.trim()
        return if (publicKey.isNullOrBlank()) {
            "$PREFIX|1|$deviceId|$safeName"
        } else {
            "$PREFIX|2|$deviceId|$safeName|$publicKey"
        }
    }

    fun decode(raw: String?): ParsedContact? {
        if (raw.isNullOrBlank()) return null
        val decoded = try {
            URLDecoder.decode(raw.trim(), Charsets.UTF_8.name())
        } catch (_: Exception) {
            raw.trim()
        }
        val parts = decoded.trim().split('|')
        if (parts.size < 4) return null
        if (!parts[0].equals(PREFIX, ignoreCase = true)) return null
        if (parts[1] != "1" && parts[1] != "2") return null
        val deviceId = parts[2].trim()
        val publicKey = if (parts[1] == "2" && parts.size >= 5) parts.last().trim() else ""
        val name = if (publicKey.isNotEmpty()) {
            parts.subList(3, parts.size - 1).joinToString("|").trim()
        } else {
            parts.drop(3).joinToString("|").trim()
        }
        if (!validId(deviceId) || name.isEmpty() || name.length > 40) return null
        if (publicKey.isNotEmpty() && SealedBox.decodeKey(publicKey) == null) return null
        return ParsedContact(deviceId, name, publicKey)
    }

    fun validId(deviceId: String): Boolean =
        deviceId.length in 8..64 && deviceId.all { it.isLetterOrDigit() || it == '-' }
}

object Identity {
    fun defaultName(model: String, deviceId: String): String {
        val cleaned = model.trim().ifBlank { "Phone" }.replace("|", " ").take(24).trim().ifBlank { "Phone" }
        return "$cleaned ${suffix(deviceId)}"
    }

    fun suffix(deviceId: String): String =
        deviceId.replace("-", "").take(4).lowercase().ifBlank { "0000" }

    fun shortCode(deviceId: String): String = "BLNK · ${suffix(deviceId).uppercase()}"

    /** Mine is the device id or a message this phone created. Names are not identity. */
    fun isMine(fromDeviceId: String, myDeviceId: String, localOrigin: Boolean): Boolean =
        localOrigin || (myDeviceId.isNotBlank() && fromDeviceId == myDeviceId)
}

/** Nearby endpoint name: `<deviceId>|<displayName>|<publicKey>`, capped without cutting the key. */
object EndpointCard {
    fun encode(deviceId: String, name: String, publicKey: String = ""): String {
        val safeName = name.trim().replace("|", " ").ifBlank { "Phone" }
        if (publicKey.isBlank()) return "$deviceId|$safeName".take(120)
        val suffix = "|$publicKey"
        val room = (120 - deviceId.length - suffix.length - 1).coerceAtLeast(1)
        return "$deviceId|${safeName.take(room)}$suffix"
    }

    fun decode(raw: String?): ParsedContact? {
        if (raw.isNullOrBlank() || '|' !in raw) return null
        val parts = raw.split('|')
        val deviceId = parts[0].trim()
        if (!ContactQr.validId(deviceId)) return null
        val key = parts.getOrNull(2)?.trim().orEmpty().takeIf { SealedBox.decodeKey(it) != null }.orEmpty()
        val name = if (key.isNotEmpty()) parts[1].trim() else parts.drop(1).joinToString("|").trim()
        if (name.isEmpty()) return null
        return ParsedContact(deviceId, name, key)
    }
}

object DirectGate {
    val chatKinds = setOf("text", "voice", "ping", "call_clip")

    fun show(message: DirectMessage, myId: String): Boolean {
        if (message.kind == Ack.KIND || message.kind == "hello") return false
        val party = message.localOrigin || message.fromDeviceId == myId || message.toDeviceId == myId
        if (!party) return false
        if (message.box.isNotBlank() && message.body.isBlank() && !message.localOrigin) return false
        return true
    }

    fun receive(message: DirectMessage): DirectMessage? {
        if (message.id.isBlank() || message.fromDeviceId.isBlank() || message.toDeviceId.isBlank()) return null
        val sealed = message.box.isNotBlank()
        if (message.kind == "text" && message.body.isBlank() && !sealed) return null
        val hop = message.hops + 1
        if (hop > RelayPolicy.MAX_HOPS) return null
        return message.copy(hops = hop, localOrigin = false, audioPath = null, clipPath = null)
    }

    /** Messages we keep in the forward queue. Delivered chats we wrote stay outside the cap. */
    fun holdsForRelay(message: DirectMessage): Boolean {
        if (message.kind == "hello") return false
        if (message.kind == Ack.KIND) return true
        return !message.localOrigin || message.delivery != Delivery.DELIVERED
    }

    /** Intermediate phones forward anyone's 1:1, including people who are not contacts. */
    fun shouldForward(message: DirectMessage, myId: String): Boolean =
        message.kind != "hello" &&
            message.hops < RelayPolicy.MAX_HOPS &&
            message.toDeviceId != myId &&
            message.fromDeviceId != myId
}

data class Outbox(
    val endpointId: String,
    val messages: List<DirectMessage>,
    val attachClips: Boolean,
    val live: Boolean,
)

object ResyncPlan {
    /** History goes only to the phone that just connected, and never with clips. */
    fun history(endpointId: String, messages: List<DirectMessage>): Outbox = Outbox(
        endpointId = endpointId,
        messages = messages.map { it.copy(audioPath = null) },
        attachClips = false,
        live = false,
    )

    fun live(endpointIds: List<String>, message: DirectMessage, withClip: Boolean): List<Outbox> =
        endpointIds.map { id ->
            Outbox(id, listOf(if (withClip) message else message.copy(audioPath = null)), withClip, live = true)
        }

    fun liveBeforeHistory(jobs: List<Outbox>): List<Outbox> = jobs.sortedByDescending { it.live }
}

object CaptionDisplay {
    /** Speaker line is the transcript itself. This phone does not translate. */
    fun text(transcript: String): String = transcript.trim()
}

object Ptt {
    const val MAX_SECONDS = 10
    const val INVITE = "call_invite"
    const val ACCEPT = "call_accept"
    const val DECLINE = "call_decline"
    const val END = "call_end"
    const val CLIP = "call_clip"

    fun triageCaption(text: String) = TriageEngine.triage(text)

    fun emergency(text: String): Boolean = triageCaption(text).urgency == Urgency.CRITICAL
}

enum class CallPhase { IDLE, OUTGOING, INCOMING, ACTIVE }

data class CallState(
    val phase: CallPhase = CallPhase.IDLE,
    val peerId: String = "",
    val peerName: String = "",
    val speakerOn: Boolean = false,
    val playing: Boolean = false,
)

object CallMachine {
    fun inviteOut(peerId: String, name: String): CallState =
        CallState(CallPhase.OUTGOING, peerId, name)

    fun inviteIn(current: CallState, fromId: String, name: String): CallState =
        if (current.phase == CallPhase.IDLE) CallState(CallPhase.INCOMING, fromId, name) else current

    fun accept(current: CallState): CallState =
        if (current.phase == CallPhase.INCOMING || current.phase == CallPhase.OUTGOING) {
            current.copy(phase = CallPhase.ACTIVE, speakerOn = false, playing = false)
        } else {
            current
        }

    fun decline(current: CallState): CallState = CallState()

    fun end(current: CallState): CallState = CallState()

    fun remoteSignal(current: CallState, message: DirectMessage, myId: String): CallState {
        if (message.toDeviceId != myId) return current
        return when (message.kind) {
            Ptt.ACCEPT -> if (current.phase == CallPhase.OUTGOING && message.fromDeviceId == current.peerId) {
                current.copy(phase = CallPhase.ACTIVE)
            } else {
                current
            }
            Ptt.DECLINE, Ptt.END -> if (message.fromDeviceId == current.peerId) CallState() else current
            else -> current
        }
    }

    fun canHold(current: CallState): Boolean = current.phase == CallPhase.ACTIVE && !current.playing
}

object VoiceControl {
    /** Stop and cancel act on the recorder only while this session is still the live one. */
    fun shouldStopRecorder(actionSession: Int, liveSession: Int, recorderRunning: Boolean): Boolean =
        actionSession == liveSession && recorderRunning
}

class DirectStore(private val persistence: DirectPersistence) {
    private val lock = Any()
    private val messages = linkedMapOf<String, DirectMessage>()
    private val contacts = linkedMapOf<String, SavedContact>()
    private var nearby: Set<String> = emptySet()
    var myId: String = ""

    /** Opens a box addressed to us. Null means the seal failed. */
    var openSealed: ((DirectMessage) -> DirectMessage?)? = null

    /** Fired outside the store lock for a decrypted message addressed to us. */
    var onAddressed: ((DirectMessage) -> Unit)? = null
    private val disk = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "saklolo-direct")
    }

    init {
        val snap = persistence.load()
        synchronized(lock) {
            snap.messages.forEach { messages[it.id] = it }
            snap.contacts.forEach { contacts[it.deviceId] = it }
        }
    }

    fun snapshot(): DirectSnapshot = synchronized(lock) {
        DirectSnapshot(contacts.values.toList(), messages.values.toList())
    }

    fun addLocal(message: DirectMessage): DirectMessage = synchronized(lock) {
        val expire = if (message.expireAtMillis > 0) message.expireAtMillis else HoldPolicy.expireAt(message.createdAtMillis)
        val stored = message.copy(hops = 0, localOrigin = true, expireAtMillis = expire)
        messages[stored.id] = stored
        enforceLocked(stored.createdAtMillis)
        persistLocked()
        stored
    }

    fun ingest(incoming: List<DirectMessage>, receivedAt: Long): List<DirectMessage> {
        val fresh = synchronized(lock) {
            val accepted = mutableListOf<DirectMessage>()
            for (message in incoming) {
                if (message.id in messages) continue
                var stored = DirectGate.receive(message) ?: continue
                if (stored.kind == "hello" && stored.body.isNotBlank()) {
                    rememberKeyLocked(stored.fromDeviceId, stored.body, stored.senderName)
                }
                if (stored.box.isNotBlank() && stored.toDeviceId == myId) {
                    val opened = openSealed?.invoke(stored)
                    stored = if (opened == null) {
                        stored.copy(body = "")
                    } else {
                        stored.copy(senderName = opened.senderName, body = opened.body)
                    }
                }
                if (stored.kind == Ack.KIND && stored.toDeviceId == myId && stored.body.isNotBlank()) {
                    applyAckLocked(stored.body)
                }
                if (stored.expireAtMillis == 0L) {
                    stored = stored.copy(expireAtMillis = HoldPolicy.expireAt(stored.createdAtMillis))
                }
                messages[stored.id] = stored
                touchLocked(stored.fromDeviceId, stored.senderName, receivedAt, saved = null)
                accepted += stored
            }
            if (accepted.isNotEmpty()) {
                enforceLocked(receivedAt)
                persistLocked()
            }
            accepted.filter { it.id in messages }
        }
        fresh.filter { shouldAck(it) }.forEach { onAddressed?.invoke(it) }
        return fresh
    }

    fun saveQr(deviceId: String, name: String, now: Long, publicKey: String = "") = synchronized(lock) {
        val current = contacts[deviceId]
        contacts[deviceId] = SavedContact(
            deviceId = deviceId,
            name = name,
            addedAtMillis = current?.addedAtMillis ?: now,
            favorite = current?.favorite ?: false,
            lastHeardMillis = current?.lastHeardMillis ?: now,
            saved = true,
            publicKey = publicKey.ifBlank { current?.publicKey.orEmpty() },
        )
        persistLocked()
    }

    fun publicKey(deviceId: String): String = synchronized(lock) { contacts[deviceId]?.publicKey.orEmpty() }

    fun rememberKey(deviceId: String, publicKey: String, name: String = "") = synchronized(lock) {
        if (!rememberKeyLocked(deviceId, publicKey, name)) return
        persistLocked()
    }

    fun markRelayed(id: String, hops: Int) = synchronized(lock) {
        val current = messages[id] ?: return
        if (current.delivery == Delivery.DELIVERED) return
        messages[id] = current.copy(delivery = Delivery.RELAYED, relayHops = hops)
        persistLocked()
    }

    /** Only an authenticated ACK may call this. */
    fun markDelivered(id: String, hops: Int) = synchronized(lock) {
        val current = messages[id] ?: return
        if (!current.localOrigin) return
        messages[id] = current.copy(delivery = Delivery.DELIVERED, relayHops = hops)
        persistLocked()
    }

    fun summaryIds(): Set<String> = synchronized(lock) { messages.keys.toSet() }

    fun exportIds(ids: Set<String>): List<DirectMessage> = synchronized(lock) {
        ids.mapNotNull { messages[it] }.map { it.copy(audioPath = null) }
    }

    /** Sealed clips travel with the message. Plaintext wav stays off the summary. */
    fun exportForRelay(ids: Set<String>): List<DirectMessage> = synchronized(lock) {
        ids.mapNotNull { messages[it] }.map { message ->
            message.copy(audioPath = message.transferPath())
        }
    }

    fun notePeer(deviceId: String, name: String, heardAt: Long) = synchronized(lock) {
        touchLocked(deviceId, name, heardAt, saved = null)
        persistLocked()
    }

    fun setNearby(ids: Set<String>) = synchronized(lock) {
        nearby = ids
    }

    fun setFavorite(deviceId: String, favorite: Boolean) = synchronized(lock) {
        val current = contacts[deviceId] ?: return
        contacts[deviceId] = current.copy(favorite = favorite)
        persistLocked()
    }

    fun remove(deviceId: String) = synchronized(lock) {
        contacts.remove(deviceId)
        persistLocked()
    }

    fun attachAudio(id: String, path: String) = synchronized(lock) {
        val current = messages[id] ?: return
        if (current.audioPath == path) return
        messages[id] = current.copy(audioPath = path)
        persistLocked()
    }

    fun attachSealedClip(id: String, path: String, bytes: Int) = synchronized(lock) {
        val current = messages[id] ?: return
        messages[id] = current.copy(clipPath = path, clipBytes = bytes.coerceAtLeast(0))
        persistLocked()
    }

    fun find(id: String): DirectMessage? = synchronized(lock) { messages[id] }

    fun visible(myId: String): List<DirectMessage> = synchronized(lock) {
        messages.values.filter { DirectGate.show(it, myId) }.sortedBy { it.createdAtMillis }
    }

    fun thread(myId: String, peerId: String): List<DirectMessage> =
        visible(myId).filter { otherId(it, myId) == peerId }

    fun relayHistory(): List<DirectMessage> = synchronized(lock) {
        messages.values.map { it.copy(audioPath = null) }.sortedBy { it.createdAtMillis }
    }

    fun rows(): List<ContactRow> = synchronized(lock) {
        contacts.values.map { contact ->
            ContactRow(
                deviceId = contact.deviceId,
                name = contact.name,
                favorite = contact.favorite,
                saved = contact.saved,
                inRange = contact.deviceId in nearby,
                lastHeardMillis = contact.lastHeardMillis,
                addedAtMillis = contact.addedAtMillis,
            )
        }.sortedWith(compareByDescending<ContactRow> { it.inRange }.thenBy { it.name.lowercase() })
    }

    fun conversations(myId: String, readAt: Map<String, Long>): List<Conversation> {
        val visible = visible(myId).filter { it.kind in DirectGate.chatKinds }
        return visible.groupBy { otherId(it, myId) }.map { (peerId, msgs) ->
            val last = msgs.maxBy { it.createdAtMillis }
            val name = synchronized(lock) { contacts[peerId]?.name }
                ?: msgs.lastOrNull { it.fromDeviceId == peerId }?.senderName
                ?: "Phone"
            val unread = msgs.count { message ->
                message.fromDeviceId == peerId &&
                    message.kind != "ping" &&
                    message.createdAtMillis > (readAt[peerId] ?: 0L)
            }
            val critical = msgs.lastOrNull { message ->
                message.fromDeviceId == peerId &&
                    message.kind != "ping" &&
                    Ptt.emergency(message.body)
            }
            Conversation(
                peerId = peerId,
                name = name,
                snippet = snippet(last),
                atMillis = last.createdAtMillis,
                unread = unread,
                critical = critical != null,
                criticalBody = critical?.body.orEmpty(),
            )
        }.sortedWith(compareByDescending<Conversation> { it.critical }.thenByDescending { it.atMillis })
    }

    private fun otherId(message: DirectMessage, myId: String): String =
        if (message.fromDeviceId == myId) message.toDeviceId else message.fromDeviceId

    private fun snippet(message: DirectMessage): String = when (message.kind) {
        "voice" -> "Voice ${message.body}".trim()
        "call_clip" -> "Voice ${message.body}".trim()
        "ping" -> "Ping"
        else -> message.body
    }

    private fun shouldAck(message: DirectMessage): Boolean =
        message.toDeviceId == myId &&
            message.kind != Ack.KIND &&
            message.kind != "hello" &&
            message.body.isNotBlank()

    private fun applyAckLocked(body: String) {
        val parsed = Ack.parse(body) ?: return
        val current = messages[parsed.first] ?: return
        if (!current.localOrigin) return
        messages[parsed.first] = current.copy(delivery = Delivery.DELIVERED, relayHops = parsed.second)
    }

    private fun touchLocked(deviceId: String, name: String, heardAt: Long, saved: Boolean?) {
        if (!ContactQr.validId(deviceId)) return
        val current = contacts[deviceId]
        contacts[deviceId] = SavedContact(
            deviceId = deviceId,
            name = name.ifBlank { current?.name ?: "Phone" },
            addedAtMillis = current?.addedAtMillis ?: heardAt,
            favorite = current?.favorite ?: false,
            lastHeardMillis = maxOf(current?.lastHeardMillis ?: 0L, heardAt),
            saved = saved ?: current?.saved ?: false,
            publicKey = current?.publicKey.orEmpty(),
        )
    }

    private fun rememberKeyLocked(deviceId: String, publicKey: String, name: String): Boolean {
        if (!ContactQr.validId(deviceId) || SealedBox.decodeKey(publicKey) == null) return false
        val current = contacts[deviceId]
        contacts[deviceId] = SavedContact(
            deviceId = deviceId,
            name = name.ifBlank { current?.name ?: "Phone" },
            addedAtMillis = current?.addedAtMillis ?: System.currentTimeMillis(),
            favorite = current?.favorite ?: false,
            lastHeardMillis = current?.lastHeardMillis ?: 0L,
            saved = current?.saved ?: false,
            publicKey = publicKey,
        )
        return true
    }

    private fun enforceLocked(now: Long) {
        val held = messages.values.filter { DirectGate.holdsForRelay(it) }.map { message ->
            Held(
                id = message.id,
                createdAtMillis = message.createdAtMillis,
                expireAtMillis = if (message.expireAtMillis > 0) message.expireAtMillis else HoldPolicy.expireAt(message.createdAtMillis),
                clipBytes = message.clipBytes,
                sos = false,
            )
        }
        val keep = HoldPolicy.evict(held, now).map { it.id }.toSet()
        held.map { it.id }.filter { it !in keep }.forEach { messages.remove(it) }
    }

    private fun persistLocked() {
        val snap = DirectSnapshot(contacts.values.toList(), messages.values.toList())
        if (persistence.async) {
            disk.execute { persistence.save(snap) }
        } else {
            persistence.save(snap)
        }
    }
}

/** One phone in the unit-test mesh. Delivery uses the same store rules as the relay. */
class MeshPhone(val deviceId: String, val name: String) {
    val store = DirectStore(MemoryDirectPersistence())
    val links = linkedSetOf<MeshPhone>()
    var call = CallState()
    val played = mutableListOf<String>()
    val sos = mutableListOf<String>()

    init {
        store.myId = deviceId
    }

    fun connect(other: MeshPhone, now: Long) {
        links += other
        other.links += this
        val mine = store.relayHistory()
        val theirs = other.store.relayHistory()
        store.notePeer(other.deviceId, other.name, now)
        other.store.notePeer(deviceId, name, now)
        store.setNearby(links.map { it.deviceId }.toSet())
        other.store.setNearby(other.links.map { it.deviceId }.toSet())
        val toOther = ResyncPlan.history(other.deviceId, mine)
        val toMe = ResyncPlan.history(deviceId, theirs)
        check(!toOther.attachClips && toOther.messages.all { it.audioPath == null })
        other.store.ingest(toOther.messages, now)
        store.ingest(toMe.messages, now)
    }

    fun send(to: MeshPhone, body: String, kind: String = "text", at: Long, audioPath: String? = null): DirectMessage {
        val message = store.addLocal(
            DirectMessage(
                id = "$deviceId-$kind-$at-$body".take(80),
                fromDeviceId = deviceId,
                toDeviceId = to.deviceId,
                senderName = name,
                body = body,
                createdAtMillis = at,
                kind = kind,
                audioPath = audioPath,
            ),
        )
        flood(message, from = null)
        return message
    }

    fun accept(message: DirectMessage, from: MeshPhone?, receivedAt: Long) {
        val fresh = store.ingest(listOf(message), receivedAt)
        val stored = fresh.firstOrNull() ?: return
        if (stored.toDeviceId == deviceId) {
            call = when (stored.kind) {
                Ptt.INVITE -> CallMachine.inviteIn(call, stored.fromDeviceId, stored.senderName)
                else -> CallMachine.remoteSignal(call, stored, deviceId)
            }
            if (stored.kind == Ptt.CLIP && call.phase == CallPhase.ACTIVE) {
                played += stored.id
            }
        }
        if (DirectGate.shouldForward(stored, deviceId)) flood(stored, from)
    }

    private fun flood(message: DirectMessage, from: MeshPhone?) {
        for (peer in links) {
            if (peer == from) continue
            peer.accept(message, this, message.createdAtMillis)
        }
    }
}

fun DirectMessage.toWire() = WireDirect(
    id = id,
    fromDeviceId = fromDeviceId,
    toDeviceId = toDeviceId,
    senderName = if (box.isNotBlank()) "" else senderName,
    body = if (box.isNotBlank()) "" else body,
    createdAtMillis = createdAtMillis,
    hops = hops,
    kind = kind,
    box = box,
    clipBytes = clipBytes,
)

fun DirectMessage.transferPath(): String? = if (box.isNotBlank()) clipPath else audioPath

fun WireDirect.toDirect(audioPath: String? = null) = DirectMessage(
    id = id,
    fromDeviceId = fromDeviceId,
    toDeviceId = toDeviceId,
    senderName = senderName,
    body = body,
    createdAtMillis = createdAtMillis,
    hops = hops,
    kind = kind,
    audioPath = audioPath,
    box = box,
    clipBytes = clipBytes,
)
