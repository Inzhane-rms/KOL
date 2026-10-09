package ph.appbuilders.saklolo

import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.audio.Loudness
import ph.appbuilders.saklolo.audio.SpeechPrep
import ph.appbuilders.saklolo.audio.WavPcm
import ph.appbuilders.saklolo.contact.Urgent
import ph.appbuilders.saklolo.group.PayloadOrder
import ph.appbuilders.saklolo.group.PieceKind
import ph.appbuilders.saklolo.group.RelayPiece
import ph.appbuilders.saklolo.relay.ReadyToConnect
import ph.appbuilders.saklolo.stt.WhisperPrompt
import ph.appbuilders.saklolo.ui.MainNav

class V0114Test {
    @Test
    fun dismissKeepsTheReadyScreenDownForTheSession() {
        assertTrue(ReadyToConnect.show(seen = false, requiredMissing = 3, pillTapped = false))
        assertFalse(ReadyToConnect.show(seen = true, requiredMissing = 3, pillTapped = false))
        assertFalse(ReadyToConnect.show(seen = true, requiredMissing = 0, pillTapped = false))
        assertTrue(ReadyToConnect.show(seen = true, requiredMissing = 3, pillTapped = true))
        assertEquals("Setup needed", ReadyToConnect.statusPill(2, 4))
    }

    @Test
    fun tabsAreContactsMessagesCallAndAdd() {
        assertEquals(listOf("contacts", "messages", "call", "add"), MainNav.tabs)
        assertFalse(MainNav.tabs.any { it.equals("sos", ignoreCase = true) })
        assertEquals("contacts", MainNav.barRoute("thread"))
        assertEquals("messages", MainNav.barRoute("messages"))
        assertEquals("add", MainNav.barRoute("add"))
    }

    @Test
    fun textAndUrgentBytesGoAheadOfFiles() {
        val plan = PayloadOrder.plan(
            inFlightFilePayloadIds = listOf(7L, 8L),
            pending = listOf(
                RelayPiece(PieceKind.FILE, "clip"),
                RelayPiece(PieceKind.TEXT_BYTES, "text"),
                RelayPiece(PieceKind.URGENT_BYTES, "urgent"),
                RelayPiece(PieceKind.VOICE_BYTES, "voice"),
            ),
        )
        assertTrue(plan.cancelFilePayloadIds.isEmpty())
        assertEquals(listOf("urgent", "text", "voice", "clip"), plan.ordered.map { it.id })
        assertEquals(PieceKind.URGENT_BYTES, plan.ordered.first().kind)
        val filesOnly = PayloadOrder.plan(listOf(1L), listOf(RelayPiece(PieceKind.FILE, "a")))
        assertTrue(filesOnly.cancelFilePayloadIds.isEmpty())
    }

    @Test
    fun textDoesNotCancelOrRestartAnInFlightClip() {
        val plan = PayloadOrder.textBesideClip(inFlightFilePayloadIds = listOf(42L, 43L), messageId = "hello")
        assertTrue(plan.cancelFilePayloadIds.isEmpty())
        assertEquals(listOf(PieceKind.TEXT_BYTES), plan.ordered.map { it.kind })
        assertEquals("hello", plan.ordered.single().id)
        val urgent = PayloadOrder.plan(listOf(9L), PayloadOrder.sequence("u", Urgent.KIND, hasAudio = false))
        assertTrue(urgent.cancelFilePayloadIds.isEmpty())
        assertEquals(PieceKind.URGENT_BYTES, urgent.ordered.single().kind)
    }

    @Test
    fun transcriptBytesPrecedeTheAudioFile() {
        val order = PayloadOrder.transcriptFirst("clip-1")
        assertEquals(listOf(PieceKind.VOICE_BYTES, PieceKind.FILE), order.map { it.kind })
        assertTrue(order.all { it.id == "clip-1" })
        val urgent = PayloadOrder.sequence("u1", Urgent.KIND, hasAudio = false)
        assertEquals(listOf(PieceKind.URGENT_BYTES), urgent.map { it.kind })
        val text = PayloadOrder.sequence("t1", "text", hasAudio = false)
        assertEquals(PieceKind.TEXT_BYTES, text.single().kind)
    }

    @Test
    fun loudnessGainTargetsMinus16AndLimitsPeaks() {
        val open = Loudness.gain(rms = 0.1, peak = 0.1)
        assertEquals(Loudness.targetLinear() / 0.1, open, 1e-9)
        val limited = Loudness.gain(rms = 0.01, peak = 0.9)
        assertEquals(Loudness.PEAK_LIMIT / 0.9, limited, 1e-9)
        val shaped = Loudness.apply(FloatArray(1000) { 0.1f })
        val peak = shaped.maxOf { kotlin.math.abs(it) }
        assertTrue(peak <= Loudness.PEAK_LIMIT.toFloat() + 0.001f)
        assertTrue(peak > 0.15f)
    }

    @Test
    fun energyVadTrimsLeadingAndTrailingSilence() {
        val rate = SpeechPrep.SAMPLE_RATE
        val silence = FloatArray(rate)
        val tone = FloatArray(rate / 2) { index ->
            (sin(2.0 * PI * 200.0 * index / rate) * 0.5).toFloat()
        }
        val clip = silence + tone + silence
        val trimmed = SpeechPrep.prepare(clip)
        assertTrue(trimmed.size < clip.size / 2)
        assertTrue(trimmed.size > rate / 8)
        assertTrue(trimmed.any { kotlin.math.abs(it) > 0.05f })
        assertEquals(0, SpeechPrep.trimSilence(FloatArray(rate)).size)
    }

    @Test
    fun whisperStaysOnTagalogWithConcertWords() {
        assertEquals("tl", WhisperPrompt.LANGUAGE)
        for (word in WhisperPrompt.WORDS) {
            assertTrue(WhisperPrompt.TEXT.contains(word))
        }
    }

    @Test
    fun receivedWavKeepsItsExtensionAndOtherClipsPlayAsM4a() {
        val dir = File.createTempFile("clips", "").apply {
            delete()
            mkdirs()
        }
        val wav = File(dir, "old.wav")
        WavPcm.write(wav, FloatArray(320) { 0.2f })
        val storedWav = WavPcm.storedClip(dir, "old", wav)
        assertTrue(storedWav.name.endsWith(".wav"))
        val m4a = File(dir, "new.m4a")
        m4a.writeBytes(byteArrayOf(0, 0, 0, 0x20, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte()))
        val stored = WavPcm.storedClip(dir, "new", m4a)
        assertTrue(stored.name.endsWith(".m4a"))
        dir.deleteRecursively()
    }
}
