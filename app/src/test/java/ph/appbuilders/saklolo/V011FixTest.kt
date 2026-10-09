package ph.appbuilders.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.contact.CallMachine
import ph.appbuilders.saklolo.contact.CallPhase
import ph.appbuilders.saklolo.contact.ClipPlay
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.Hangup
import ph.appbuilders.saklolo.contact.NameChoice
import ph.appbuilders.saklolo.contact.Ptt

class V011FixTest {
    @Test
    fun captionDoesNotConsumeTheClipAndAudioPlaysOnce() {
        var played = emptySet<String>()
        assertNull(ClipPlay.pending("clip-1", audioPath = null, played))
        played = ClipPlay.remember("clip-1", started = false, played)
        assertEquals("/clips/clip-1.wav", ClipPlay.pending("clip-1", "/clips/clip-1.wav", played))
        played = ClipPlay.remember("clip-1", started = false, played)
        assertEquals("/clips/clip-1.wav", ClipPlay.pending("clip-1", "/clips/clip-1.wav", played))
        played = ClipPlay.remember("clip-1", started = true, played)
        assertTrue("clip-1" in played)
        assertNull(ClipPlay.pending("clip-1", "/clips/clip-1.wav", played))
    }

    @Test
    fun everyHangupNotifiesAndRingExpiresAtThirtySeconds() {
        assertEquals(Ptt.DECLINE, Hangup.notify(CallPhase.OUTGOING))
        assertEquals(Ptt.DECLINE, Hangup.notify(CallPhase.INCOMING))
        assertEquals(Ptt.END, Hangup.notify(CallPhase.ACTIVE))
        assertNull(Hangup.notify(CallPhase.IDLE))
        assertFalse(Hangup.expired(1_000, 1_000 + Hangup.RING_MS - 1))
        assertTrue(Hangup.expired(1_000, 1_000 + Hangup.RING_MS))

        val me = "phone-ana01"
        val ben = "phone-ben01"
        val cancel = DirectMessage(
            id = "cancel-1",
            fromDeviceId = ben,
            toDeviceId = me,
            senderName = "Ben",
            body = "Decline",
            createdAtMillis = 5,
            kind = Ptt.DECLINE,
        )
        val outgoing = CallMachine.inviteOut(ben, "Ben")
        assertEquals(CallPhase.IDLE, CallMachine.remoteSignal(outgoing, cancel, me).phase)
        val incoming = CallMachine.inviteIn(outgoing.copy(phase = CallPhase.IDLE), ben, "Ben")
        assertEquals(CallPhase.INCOMING, incoming.phase)
        assertEquals(CallPhase.IDLE, CallMachine.remoteSignal(incoming, cancel, me).phase)
        assertEquals(Ptt.DECLINE, Hangup.notify(incoming.phase))
    }

    @Test
    fun namePromptShowsOnceAndKeepsTheDeviceId() {
        assertTrue(NameChoice.show(chosen = false))
        assertFalse(NameChoice.show(chosen = true))
        assertEquals(NameChoice.PROMPT, "What should friends see?")
        val (id, name) = NameChoice.saved("phone-ana01", "Ana Cruz", "Phone abcd")
        assertEquals("phone-ana01", id)
        assertEquals("Ana Cruz", name)
        val (same, kept) = NameChoice.saved("phone-ana01", "   ", "Phone abcd")
        assertEquals("phone-ana01", same)
        assertEquals("Phone abcd", kept)
    }
}
