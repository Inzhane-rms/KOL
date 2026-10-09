package ph.appbuilders.saklolo.group

import ph.appbuilders.saklolo.relay.RelayPolicy
import ph.appbuilders.saklolo.triage.Urgency

data class GroupSnapshot(
    val groups: List<ConcertGroup> = emptyList(),
    val notes: List<GroupNote> = emptyList(),
    val sightings: List<Sighting> = emptyList(),
    val localOriginIds: List<String> = emptyList(),
    val activeId: String? = null,
)

interface GroupPersistence {
    fun load(): GroupSnapshot
    fun save(snapshot: GroupSnapshot)
}

class MemoryGroupPersistence : GroupPersistence {
    private var snapshot = GroupSnapshot()
    override fun load(): GroupSnapshot = snapshot
    override fun save(snapshot: GroupSnapshot) {
        this.snapshot = snapshot
    }
}

/**
 * Groups are keyed by random id. Messages from other groups are kept so they can
 * be relayed, and hidden from [visible]. Last-seen uses the clock passed in at
 * arrival, including messages this phone only relays.
 */
class GroupStore(private val persistence: GroupPersistence) {
    private val groups = linkedMapOf<String, ConcertGroup>()
    private val notes = linkedMapOf<String, GroupNote>()
    private val localOriginIds = mutableSetOf<String>()
    private val lastSeen = LastSeenBook()
    private var activeId: String? = null

    init {
        val loaded = try {
            persistence.load()
        } catch (_: Exception) {
            GroupSnapshot()
        }
        loaded.groups.forEach { groups[it.id] = it }
        loaded.notes.forEach { notes[it.id] = it }
        localOriginIds += loaded.localOriginIds
        lastSeen.load(loaded.sightings)
        activeId = loaded.activeId?.takeIf { it in groups } ?: groups.values.maxByOrNull { it.joinedAtMillis }?.id
    }

    fun groups(): List<ConcertGroup> = synchronized(this) { groups.values.toList() }

    fun memberIds(): Set<String> = synchronized(this) { groups.keys.toSet() }

    fun active(): ConcertGroup? = synchronized(this) { activeId?.let { groups[it] } }

    fun setActive(id: String) = synchronized(this) {
        if (id !in groups) return
        activeId = id
        persist()
    }

    /** Same id joins once. A second group with the same display name is a different id. */
    fun join(id: String, name: String, at: Long, createdHere: Boolean = false): ConcertGroup = synchronized(this) {
        val trimmed = name.trim()
        val existing = groups[id]
        val stored = if (existing == null) {
            ConcertGroup(id, trimmed, at, createdHere)
        } else {
            existing.copy(
                name = trimmed.ifBlank { existing.name },
                createdHere = existing.createdHere || createdHere,
            )
        }
        groups[id] = stored
        activeId = id
        persist()
        stored
    }

    fun addLocal(note: GroupNote) = synchronized(this) {
        val stored = note.copy(
            hops = 0,
            urgency = labeled(note),
        )
        notes[stored.id] = stored
        localOriginIds += stored.id
        lastSeen.observe(stored, stored.createdAtMillis)
        persist()
    }

    /**
     * Returns notes that should be forwarded, including groups this phone has not joined.
     * [receivedAtMillis] is this phone's clock, not the sender's timestamp.
     */
    fun ingest(incoming: List<GroupNote>, receivedAtMillis: Long): List<GroupNote> = synchronized(this) {
        val forward = mutableListOf<GroupNote>()
        var changed = false
        for (note in incoming) {
            if (!GroupGate.relayNote(note) || note.id in notes) continue
            val hop = note.hops + 1
            if (hop > RelayPolicy.MAX_HOPS) continue
            val stored = note.copy(
                hops = hop,
                audioPath = null,
                urgency = labeled(note),
            )
            notes[stored.id] = stored
            lastSeen.observe(stored, receivedAtMillis)
            changed = true
            if (stored.hops < RelayPolicy.MAX_HOPS) forward += stored
        }
        if (changed) persist()
        forward
    }

    fun visible(): List<GroupNote> = synchronized(this) {
        val members = groups.keys
        notes.values
            .filter { GroupGate.showNote(it.groupId, members) }
            .sortedBy { it.createdAtMillis }
    }

    fun find(id: String): GroupNote? = synchronized(this) { notes[id] }

    fun attachAudio(id: String, path: String) = synchronized(this) {
        val current = notes[id] ?: return
        if (current.audioPath == path) return
        notes[id] = current.copy(audioPath = path)
        persist()
    }

    fun sightings(groupId: String): List<Sighting> = synchronized(this) { lastSeen.forGroup(groupId) }

    fun allSightings(): List<Sighting> = synchronized(this) { lastSeen.snapshot() }

    /** Notes this phone should forward, including groups it has not joined. */
    fun relayable(): List<GroupNote> = synchronized(this) {
        notes.values.filter { it.hops < RelayPolicy.MAX_HOPS }
    }

    private fun labeled(note: GroupNote): Urgency? {
        if (note.kind == "ping") return null
        return note.urgency ?: GroupTriage.label(note.body)
    }

    private fun persist() {
        persistence.save(
            GroupSnapshot(
                groups = groups.values.toList(),
                notes = notes.values.toList(),
                sightings = lastSeen.snapshot(),
                localOriginIds = localOriginIds.toList(),
                activeId = activeId,
            ),
        )
    }
}
