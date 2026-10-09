package ph.appbuilders.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.group.ClipGate
import ph.appbuilders.saklolo.group.GroupGate
import ph.appbuilders.saklolo.group.GroupNote
import ph.appbuilders.saklolo.group.GroupQr
import ph.appbuilders.saklolo.group.GroupStore
import ph.appbuilders.saklolo.group.GroupTriage
import ph.appbuilders.saklolo.group.NoteRelay
import ph.appbuilders.saklolo.group.VoiceDraft
import ph.appbuilders.saklolo.group.MemoryGroupPersistence
import ph.appbuilders.saklolo.group.PieceKind
import ph.appbuilders.saklolo.group.RecordIntent
import ph.appbuilders.saklolo.group.RelayPiece
import ph.appbuilders.saklolo.group.SosDispatch
import ph.appbuilders.saklolo.group.recordIntent
import ph.appbuilders.saklolo.model.AlertJson
import ph.appbuilders.saklolo.stt.PcmRecorder
import ph.appbuilders.saklolo.triage.Urgency

class ConcertLogicTest {
    @Test
    fun qrCarriesVersionRandomIdAndNameAndRejectsForeignCodes() {
        val id = GroupQr.newId()
        assertTrue(GroupQr.isRandomGroupId(id))
        val encoded = GroupQr.encode(id, "Barkada")
        assertTrue(encoded.startsWith("BLK1|1|$id|"))
        val card = GroupQr.decode(encoded)
        assertEquals(id, card?.id)
        assertEquals("Barkada", card?.name)

        assertNull(GroupQr.decode("https://example.com/join"))
        assertNull(GroupQr.decode("SKL1|abc|CRITICAL|1|||0|help"))
        assertNull(GroupQr.decode("BLK1|9|$id|Barkada"))
        assertNull(GroupQr.decode("BLK1|1|Barkada|Barkada"))
        assertNull(GroupQr.decode("Barkada"))
        assertNull(GroupQr.decode(""))
        assertNull(GroupQr.decode("|||"))

        val store = GroupStore(MemoryGroupPersistence())
        store.join(id, "Barkada", 1)
        store.join(id, "Barkada", 2)
        assertEquals(1, store.groups().size)
        val other = GroupQr.newId()
        store.join(other, "Barkada", 3)
        assertEquals(2, store.groups().size)
        assertEquals(setOf(id, other), store.groups().map { it.id }.toSet())
        assertTrue(store.groups().all { it.name == "Barkada" })
    }

    @Test
    fun groupIdFiltersDisplayNotRelayAndSosHasNoGroup() {
        val store = GroupStore(MemoryGroupPersistence())
        val mine = GroupQr.newId()
        store.join(mine, "Barkada", 1)
        val other = GroupQr.newId()
        val foreign = note(other, "hello from the rail")
        val forwarded = store.ingest(listOf(foreign), receivedAtMillis = 10)
        assertEquals(listOf(foreign.id), forwarded.map { it.id })
        assertTrue(GroupGate.relayNote(foreign))
        assertFalse(GroupGate.showNote(other, store.memberIds()))
        assertTrue(store.visible().isEmpty())

        val local = note(mine, "hi barkada")
        store.addLocal(local)
        assertEquals(listOf(local.id), store.visible().map { it.id })

        val sos = GroupTriage.sendToMedics(note(mine, "nahimatay si Ana"), 50)
        val encoded = AlertJson.encodeEnvelope(listOf(sos))
        assertFalse(encoded.contains("groupId"))
        assertFalse(encoded.contains(mine))
        assertTrue(GroupGate.showSos())
    }

    @Test
    fun urgentGroupTextAndVoiceCanBeSentToMedics() {
        val body = "nahimatay si Ana"
        assertEquals(Urgency.CRITICAL, GroupTriage.label(body))
        assertEquals(GroupTriage.label(body), GroupTriage.labelVoice(body))
        val note = note(GroupQr.newId(), body)
        val sos = GroupTriage.sendToMedics(note, 99)
        assertEquals(Urgency.CRITICAL, sos.urgency)
        assertTrue(sos.transcript.contains("nahimatay"))
        assertFalse(AlertJson.encodeEnvelope(listOf(sos)).contains(note.groupId))
    }

    @Test
    fun lastSeenUsesArrivalClockAndUpdatesWhenRelayed() {
        val store = GroupStore(MemoryGroupPersistence())
        val groupId = GroupQr.newId()
        val first = note(groupId, "andito pa ako").copy(createdAtMillis = 1_000, sender = "Ana")
        store.ingest(listOf(first), receivedAtMillis = 50_000)
        val seen = store.sightings(groupId).single()
        assertEquals(50_000, seen.heardAtMillis)
        assertNotEquals(first.createdAtMillis, seen.heardAtMillis)
        assertTrue(store.visible().isEmpty())

        val again = first.copy(id = "relay-2", createdAtMillis = 2_000)
        store.ingest(listOf(again), receivedAtMillis = 80_000)
        assertEquals(80_000, store.sightings(groupId).single().heardAtMillis)
    }

    @Test
    fun sosBytesPreemptInFlightClipsAndRecordingStopsAtThirtySeconds() {
        val plan = SosDispatch.plan(
            inFlightFilePayloadIds = listOf(7L, 8L),
            pending = listOf(
                RelayPiece(PieceKind.FILE, "clip"),
                RelayPiece(PieceKind.SOS_BYTES, "sos"),
                RelayPiece(PieceKind.VOICE_BYTES, "voice"),
            ),
        )
        assertEquals(listOf(7L, 8L), plan.cancelFilePayloadIds)
        assertEquals(listOf("sos", "voice", "clip"), plan.ordered.map { it.id })
        assertEquals(PieceKind.SOS_BYTES, plan.ordered.first().kind)
        assertEquals(30, ClipGate.MAX_SECONDS)
        assertEquals(30, PcmRecorder.MAX_SECONDS)
        assertFalse(ClipGate.acceptLength(ClipGate.MAX_CLIP_BYTES + 1))
        assertTrue(ClipGate.acceptLength(1_000))
        assertEquals(RecordIntent.OpenQrJoin, recordIntent(0))
        assertEquals(RecordIntent.RecordVoice, recordIntent(1))
    }

    @Test
    fun noteHistorySplitsIntoChunksUnder32Kb() {
        val notes = (1..200).map { index ->
            GroupNote(
                id = "n$index",
                groupId = "group-history",
                sender = "Ana",
                body = "late joiner history $index " + "x".repeat(80),
                createdAtMillis = index.toLong(),
            )
        }
        assertTrue(NoteRelay.envelopeSize(notes) > NoteRelay.MAX_BYTES)
        assertEquals(32 * 1024, NoteRelay.MAX_BYTES)
        val chunks = NoteRelay.chunks(notes)
        assertTrue(chunks.size > 1)
        assertEquals(notes.map { it.id }, chunks.flatten().map { it.id })
        for (chunk in chunks) {
            assertTrue(NoteRelay.envelopeSize(chunk) <= NoteRelay.MAX_BYTES)
        }
    }

    @Test
    fun cancelDuringTranscribeDiscardsTheClip() {
        val discarded = VoiceDraft.decide(startedEpoch = 1, currentEpoch = 2, transcript = "Nasa gate ako")
        assertTrue(discarded is VoiceDraft.Finish.Discarded)
        val kept = VoiceDraft.decide(startedEpoch = 4, currentEpoch = 4, transcript = "  Nasa gate ako  ")
        assertEquals("Nasa gate ako", (kept as VoiceDraft.Finish.Keep).body)
        assertTrue(VoiceDraft.decide(4, 4, "   ") is VoiceDraft.Finish.Empty)
    }

    private fun note(groupId: String, body: String) = GroupNote(
        id = "note-" + body.hashCode(),
        groupId = groupId,
        sender = "Bea",
        body = body,
        createdAtMillis = 1_000,
    )
}
