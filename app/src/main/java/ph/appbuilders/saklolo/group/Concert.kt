package ph.appbuilders.saklolo.group

import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.model.AlertJson
import ph.appbuilders.saklolo.model.WireNote
import ph.appbuilders.saklolo.relay.RelayPolicy
import ph.appbuilders.saklolo.triage.TriageEngine
import ph.appbuilders.saklolo.triage.Urgency
import java.util.UUID

data class ConcertGroup(
    val id: String,
    val name: String,
    val joinedAtMillis: Long,
    val createdHere: Boolean = false,
)

data class GroupNote(
    val id: String,
    val groupId: String,
    val sender: String,
    val body: String,
    val createdAtMillis: Long,
    val hops: Int = 0,
    val audioPath: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val urgency: Urgency? = null,
    /** "text", "voice", or "ping". Ping is a presence note, not an alert. */
    val kind: String = "text",
)

data class Sighting(
    val groupId: String,
    val name: String,
    val heardAtMillis: Long,
    val lat: Double? = null,
    val lon: Double? = null,
)

enum class RecordIntent { OpenQrJoin, RecordVoice }

/** The center Record button opens QR join until this phone is in a group. */
fun recordIntent(joinedGroupCount: Int): RecordIntent =
    if (joinedGroupCount <= 0) RecordIntent.OpenQrJoin else RecordIntent.RecordVoice

/**
 * Group join QR. Version, a random group id, and the display name.
 *
 * BLK1|1|<groupId>|<name>
 *
 * Groups are stored by [groupId], never by name. Foreign and unknown versions return null.
 */
object GroupQr {
    const val PREFIX = "BLK1"
    const val VERSION = 1

    private val ID_PATTERN =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    data class Card(val id: String, val name: String)

    fun newId(): String = UUID.randomUUID().toString()

    fun isRandomGroupId(id: String): Boolean = ID_PATTERN.matches(id)

    fun encode(id: String, name: String): String {
        val safe = name.replace("|", " ").replace("\n", " ").trim().take(40)
        return listOf(PREFIX, VERSION.toString(), id, safe).joinToString("|")
    }

    fun decode(payload: String): Card? {
        val parts = payload.trim().split("|")
        if (parts.size < 4 || parts[0] != PREFIX) return null
        if (parts[1].toIntOrNull() != VERSION) return null
        val id = parts[2].trim()
        if (!isRandomGroupId(id)) return null
        val name = parts.subList(3, parts.size).joinToString("|").trim()
        if (name.isBlank()) return null
        return Card(id, name)
    }
}

object GroupGate {
    /** Membership is checked only for what this phone shows. */
    fun showNote(groupId: String, memberIds: Set<String>): Boolean = groupId in memberIds

    /** Relay does not look at membership. Hop limit still applies. */
    fun relayNote(note: GroupNote): Boolean =
        note.id.isNotBlank() && note.groupId.isNotBlank() && note.hops < RelayPolicy.MAX_HOPS

    /** An SOS has no group id and is shown on every phone. */
    fun showSos(): Boolean = true
}

object GroupTriage {
    fun label(body: String): Urgency? {
        val result = TriageEngine.triage(body)
        if (!result.actionable || result.urgency == Urgency.SAFE) return null
        return result.urgency
    }

    /** Voice transcripts use the same rules as typed text. */
    fun labelVoice(transcript: String): Urgency? = label(transcript)

    /** One-tap promote. The SOS alert carries no group id. */
    fun sendToMedics(note: GroupNote, nowMillis: Long): Alert {
        val result = TriageEngine.triage(note.body)
        return Alert(
            id = UUID.randomUUID().toString(),
            transcript = note.body.take(800),
            summary = result.summary.take(180),
            urgency = if (result.actionable) result.urgency else Urgency.NEEDS_HELP,
            createdAtMillis = nowMillis,
            lat = note.lat,
            lon = note.lon,
            hops = 0,
            language = "GROUP",
        )
    }
}

class LastSeenBook {
    private val rows = linkedMapOf<String, Sighting>()

    fun observe(note: GroupNote, receivedAtMillis: Long) {
        if (note.sender.isBlank() || note.groupId.isBlank()) return
        val key = note.groupId + "\u0000" + note.sender
        val prev = rows[key]
        rows[key] = Sighting(
            groupId = note.groupId,
            name = note.sender,
            heardAtMillis = receivedAtMillis,
            lat = note.lat ?: prev?.lat,
            lon = note.lon ?: prev?.lon,
        )
    }

    fun get(groupId: String, name: String): Sighting? = rows[groupId + "\u0000" + name]

    fun forGroup(groupId: String): List<Sighting> =
        rows.values.filter { it.groupId == groupId }.sortedByDescending { it.heardAtMillis }

    fun snapshot(): List<Sighting> = rows.values.toList()

    fun load(existing: List<Sighting>) {
        rows.clear()
        existing.forEach { rows[it.groupId + "\u0000" + it.name] = it }
    }
}

enum class PieceKind { SOS_BYTES, VOICE_BYTES, FILE }

data class RelayPiece(val kind: PieceKind, val id: String)

data class SendPlan(
    val cancelFilePayloadIds: List<Long>,
    val ordered: List<RelayPiece>,
)

object SosDispatch {
    /**
     * SOS is its own BYTES payload and is sent before any FILE.
     * In-flight FILE payload ids are cancelled first so the bytes are not queued behind a clip.
     */
    fun plan(inFlightFilePayloadIds: List<Long>, pending: List<RelayPiece>): SendPlan {
        val sos = pending.filter { it.kind == PieceKind.SOS_BYTES }
        val voice = pending.filter { it.kind == PieceKind.VOICE_BYTES }
        val files = pending.filter { it.kind == PieceKind.FILE }
        val cancel = if (sos.isNotEmpty()) inFlightFilePayloadIds else emptyList()
        return SendPlan(cancel, sos + voice + files)
    }
}

object ClipGate {
    const val MAX_SECONDS = 30
    const val MAX_CLIP_BYTES = 1_000_000L
    const val MIN_CLIP_BYTES = 45L

    fun acceptLength(length: Long): Boolean = length in MIN_CLIP_BYTES..MAX_CLIP_BYTES
}

/** Nearby BYTES payloads must stay under 32 KB. History is sent as several envelopes. */
object NoteRelay {
    const val MAX_BYTES = 32 * 1024

    fun chunks(notes: List<GroupNote>, maxBytes: Int = MAX_BYTES): List<List<GroupNote>> {
        val chunks = ArrayList<List<GroupNote>>()
        var current = ArrayList<GroupNote>()
        for (note in notes) {
            val candidate = current + note
            val tooBig = envelopeSize(candidate) > maxBytes
            if (tooBig && current.isNotEmpty()) {
                chunks += current
                current = arrayListOf(note)
            } else {
                current.add(note)
            }
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    fun envelopeSize(notes: List<GroupNote>): Int =
        AlertJson.encodeEnvelope(emptyList(), emptyList(), notes.map { it.toWire() })
            .toByteArray(Charsets.UTF_8).size
}

/**
 * A voice note is kept only when cancel did not move the epoch while Whisper ran.
 * A moved epoch means the clip is discarded and the transcript is ignored.
 */
object VoiceDraft {
    fun decide(startedEpoch: Int, currentEpoch: Int, transcript: String): Finish {
        if (startedEpoch != currentEpoch) return Finish.Discarded
        val body = transcript.trim()
        if (body.isEmpty()) return Finish.Empty
        return Finish.Keep(body)
    }

    sealed class Finish {
        data class Keep(val body: String) : Finish()
        data object Discarded : Finish()
        data object Empty : Finish()
    }
}

fun WireNote.toGroupNote(): GroupNote = GroupNote(
    id = id,
    groupId = groupId,
    sender = sender,
    body = body,
    createdAtMillis = createdAtMillis,
    hops = hops,
    lat = lat,
    lon = lon,
    kind = kind,
)

fun GroupNote.toWire(): WireNote = WireNote(
    id = id,
    groupId = groupId,
    sender = sender,
    body = body,
    createdAtMillis = createdAtMillis,
    hops = hops,
    lat = lat,
    lon = lon,
    kind = kind,
)
