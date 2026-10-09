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
import ph.appbuilders.saklolo.group.VoiceSheet
import ph.appbuilders.saklolo.group.MemoryGroupPersistence
import ph.appbuilders.saklolo.group.RecordIntent
import ph.appbuilders.saklolo.group.recordIntent
import ph.appbuilders.saklolo.model.AlertJson
import ph.appbuilders.saklolo.stt.PcmRecorder
import ph.appbuilders.saklolo.triage.Urgency

class ConcertLogicTest {
    @Test
    fun recordingStopsAtThirtySeconds() {
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

    @Test
    fun discardedTranscriptLeavesANewerRecordingOnTheMic() {
        val newer = VoiceSheet(session = 2, recording = true, status = "", transcript = "")
        assertEquals(newer, VoiceDraft.afterDiscard(ownerSession = 1, current = newer))
        assertTrue(newer.recording)

        val transcribing = VoiceSheet(session = 1, recording = false, status = "Transcribing…", transcript = "")
        val cleared = VoiceDraft.afterDiscard(ownerSession = 1, current = transcribing)
        assertFalse(cleared.recording)
        assertEquals("", cleared.status)
        assertEquals("", cleared.transcript)
    }
}
