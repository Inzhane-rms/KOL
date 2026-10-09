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
    /** Whisper text before lexicon correction. Travels with the message. */
    val rawBody: String? = null,
    /** Millis when Nearby accepted the payload. 0 until that handoff. Never cleared, and not on the wire. */
    val sentAtMillis: Long = 0,
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
    val urgent: Boolean = false,
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

data class ParsedContact(val deviceId: String, val name: String)

/** Personal QR: `BLKC|1|<deviceId>|<name>`. Group codes are rejected. */
object ContactQr {
    const val PREFIX = "BLKC"
    private const val VERSION = "1"

    fun encode(deviceId: String, name: String): String = "$PREFIX|$VERSION|$deviceId|${name.trim()}"

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
        if (parts[1] != VERSION) return null
        val deviceId = parts[2].trim()
        val name = parts.drop(3).joinToString("|").trim()
        if (!validId(deviceId) || name.isEmpty() || name.length > 40) return null
        return ParsedContact(deviceId, name)
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

/** Nearby endpoint name: `<deviceId>|<displayName>`, capped for the Nearby name limit. */
object EndpointCard {
    fun encode(deviceId: String, name: String): String {
        val safeName = name.trim().replace("|", " ").ifBlank { "Phone" }
        return "$deviceId|$safeName".take(120)
    }

    fun decode(raw: String?): ParsedContact? {
        if (raw.isNullOrBlank() || '|' !in raw) return null
        val deviceId = raw.substringBefore('|').trim()
        val name = raw.substringAfter('|').trim()
        if (!ContactQr.validId(deviceId) || name.isEmpty()) return null
        return ParsedContact(deviceId, name)
    }
}

object DirectGate {
    val chatKinds = setOf("text", "voice", "ping", "call_clip", Urgent.KIND)

    fun show(message: DirectMessage, myId: String): Boolean =
        message.localOrigin || message.fromDeviceId == myId || message.toDeviceId == myId

    fun receive(message: DirectMessage): DirectMessage? {
        if (message.id.isBlank() || message.fromDeviceId.isBlank() || message.toDeviceId.isBlank()) return null
        if (message.kind == "text" && message.body.isBlank()) return null
        val hop = message.hops + 1
        if (hop > RelayPolicy.MAX_HOPS) return null
        return message.copy(hops = hop, localOrigin = false, audioPath = null)
    }

    /** Intermediate phones forward. The recipient and the sender do not. */
    fun shouldForward(message: DirectMessage, myId: String): Boolean =
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

/** Shown when a transcript is expanded. Hidden when the raw line is blank or the same as the shown line. */
object OriginalCaption {
    fun line(shown: String, raw: String?): String? {
        val source = raw?.trim().orEmpty()
        if (source.isEmpty()) return null
        if (source.equals(shown.trim(), ignoreCase = true)) return null
        return "Original: $source"
    }
}

object Urgent {
    const val KIND = "urgent"
    const val BADGE = "Urgent"

    fun flagged(kind: String): Boolean = kind == KIND
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

    /** Higher of the shown line and the stored Whisper line. */
    fun either(body: String, raw: String?): Boolean =
        emergency(body) || (!raw.isNullOrBlank() && emergency(raw))
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

/** A clip is played only after its audio starts. A caption with no file stays unplayed. */
object ClipPlay {
    fun pending(id: String, audioPath: String?, played: Set<String>): String? {
        if (id.isBlank() || id in played) return null
        return audioPath?.takeIf { it.isNotBlank() }
    }

    fun remember(id: String, started: Boolean, played: Set<String>): Set<String> =
        if (started && id.isNotBlank()) played + id else played
}

/** Every hangup tells the peer. Ringing sends decline. A connected call sends end. */
object Hangup {
    const val RING_MS = 30_000L

    fun notify(phase: CallPhase): String? = when (phase) {
        CallPhase.OUTGOING, CallPhase.INCOMING -> Ptt.DECLINE
        CallPhase.ACTIVE -> Ptt.END
        CallPhase.IDLE -> null
    }

    fun expired(startedAtMillis: Long, now: Long): Boolean =
        startedAtMillis > 0L && now - startedAtMillis >= RING_MS
}

object NameChoice {
    const val PROMPT = "What should friends see?"

    fun show(chosen: Boolean): Boolean = !chosen

    /** The device id is returned unchanged. A blank name keeps the current one. */
    fun saved(deviceId: String, typed: String, fallback: String): Pair<String, String> {
        val name = typed.trim().replace("|", " ").take(40).ifEmpty { fallback }
        return deviceId to name
    }
}

object VoiceControl {
    /** Stop and cancel act on the recorder only while this session is still the live one. */
    fun shouldStopRecorder(actionSession: Int, liveSession: Int, recorderRunning: Boolean): Boolean =
        actionSession == liveSession && recorderRunning
}

/** A clip may be saved or sent only while the session that started it is still the live one. */
object ClipCommit {
    fun allow(started: Int, live: Int): Boolean = started == live
}

/** Counts a wipe so an in-flight clip sees that its session moved. */
data class WipeSessions(val hold: Int, val epoch: Int, val voice: Int, val record: Int) {
    fun bump(): WipeSessions = WipeSessions(hold + 1, epoch + 1, voice + 1, record + 1)
}

/** A second Delete all data tap does nothing while the first wipe is still running. */
object DeleteTap {
    fun accept(inProgress: Boolean): Boolean = !inProgress
}

/** Agreement stays here so a recreated screen still shows it after the wipe. */
data class WipeUi(val inProgress: Boolean, val needsAgreement: Boolean) {
    fun started(): WipeUi = copy(inProgress = true)

    fun finished(): WipeUi = copy(inProgress = false, needsAgreement = true)

    fun accepted(): WipeUi = copy(needsAgreement = false)
}

/** The wipe must return while a transcription still holds the recorder lock. */
object WipeLaunch {
    const val END_CALL_WINDOW_MS = 500L

    /** False when the lock is already held. The caller does not wait. */
    fun tryStop(gate: kotlinx.coroutines.sync.Mutex, stop: () -> Unit): Boolean {
        if (!gate.tryLock()) return false
        try {
            stop()
        } finally {
            gate.unlock()
        }
        return true
    }

    /** End-call goes out, then the relay stops. With no call, the relay stops immediately. */
    fun finishCallThenStop(
        callActive: Boolean,
        sendEnd: () -> Unit,
        waitForSend: () -> Unit,
        stopRelay: () -> Unit,
    ) {
        if (callActive) {
            sendEnd()
            waitForSend()
        }
        stopRelay()
    }
}

class DirectStore(private val persistence: DirectPersistence) {
    private val lock = Any()
    private val messages = linkedMapOf<String, DirectMessage>()
    private val contacts = linkedMapOf<String, SavedContact>()
    private var nearby: Set<String> = emptySet()
    private var commitEpoch = 0
    var myId: String = ""
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

    fun wipe() = synchronized(lock) {
        messages.clear()
        contacts.clear()
        nearby = emptySet()
        persistLocked()
    }

    fun snapshot(): DirectSnapshot = synchronized(lock) {
        DirectSnapshot(contacts.values.toList(), messages.values.toList())
    }

    fun epoch(): Int = synchronized(lock) { commitEpoch }

    fun bumpEpoch(): Int = synchronized(lock) { ++commitEpoch }

    fun addLocal(message: DirectMessage): DirectMessage = synchronized(lock) { commitLocked(message) }

    /** Returns null when [epoch] is older than a wipe, so a stale clip cannot land after the wipe. */
    fun addIfCurrent(message: DirectMessage, epoch: Int): DirectMessage? = synchronized(lock) {
        if (epoch != commitEpoch) return null
        commitLocked(message)
    }

    private fun commitLocked(message: DirectMessage): DirectMessage {
        val stored = message.copy(hops = 0, localOrigin = true)
        messages[stored.id] = stored
        persistLocked()
        return stored
    }

    /** Sets the handoff time once. A later call never clears or replaces it. */
    fun markSent(ids: Collection<String>, now: Long): Boolean = synchronized(lock) {
        if (now <= 0L) return false
        var changed = false
        for (id in ids) {
            val current = messages[id] ?: continue
            if (current.sentAtMillis > 0L) continue
            messages[id] = current.copy(sentAtMillis = now)
            changed = true
        }
        if (changed) persistLocked()
        return changed
    }

    fun ingest(incoming: List<DirectMessage>, receivedAt: Long): List<DirectMessage> = synchronized(lock) {
        val fresh = mutableListOf<DirectMessage>()
        for (message in incoming) {
            if (message.id in messages) continue
            val stored = DirectGate.receive(message) ?: continue
            messages[stored.id] = stored
            touchLocked(stored.fromDeviceId, stored.senderName, receivedAt, saved = null)
            fresh += stored
        }
        if (fresh.isNotEmpty()) persistLocked()
        fresh
    }

    fun saveQr(deviceId: String, name: String, now: Long) = synchronized(lock) {
        val current = contacts[deviceId]
        contacts[deviceId] = SavedContact(
            deviceId = deviceId,
            name = name,
            addedAtMillis = current?.addedAtMillis ?: now,
            favorite = current?.favorite ?: false,
            lastHeardMillis = current?.lastHeardMillis ?: now,
            saved = true,
        )
        persistLocked()
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
                    Ptt.either(message.body, message.rawBody)
            }
            Conversation(
                peerId = peerId,
                name = name,
                snippet = snippet(last),
                atMillis = last.createdAtMillis,
                unread = unread,
                critical = critical != null,
                criticalBody = critical?.body.orEmpty(),
                urgent = msgs.any { Urgent.flagged(it.kind) },
            )
        }.sortedWith(compareByDescending<Conversation> { it.critical }.thenByDescending { it.atMillis })
    }

    private fun otherId(message: DirectMessage, myId: String): String =
        if (message.fromDeviceId == myId) message.toDeviceId else message.fromDeviceId

    private fun snippet(message: DirectMessage): String = when (message.kind) {
        "voice" -> "Voice ${message.body}".trim()
        "call_clip" -> "Voice ${message.body}".trim()
        "ping" -> "Ping"
        Urgent.KIND -> message.body
        else -> message.body
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
        )
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
    senderName = senderName,
    body = body,
    createdAtMillis = createdAtMillis,
    hops = hops,
    kind = kind,
    rawBody = rawBody,
)

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
    rawBody = rawBody,
)
