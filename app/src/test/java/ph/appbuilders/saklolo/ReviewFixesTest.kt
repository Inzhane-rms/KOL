package ph.appbuilders.saklolo

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.audio.WavPcm
import ph.appbuilders.saklolo.relay.PeerFilter
import ph.appbuilders.saklolo.relay.RelayEndpoints
import ph.appbuilders.saklolo.relay.parseAllowlist
import ph.appbuilders.saklolo.stt.SpeechLanguage
import ph.appbuilders.saklolo.triage.SummaryRefine

class ReviewFixesTest {
    @Test
    fun bisayaUsesTagalogWhisperCode() {
        assertEquals("tl", SpeechLanguage.BISAYA.whisperCode)
        assertEquals("tl", SpeechLanguage.TAGALOG.whisperCode)
        assertEquals("en", SpeechLanguage.ENGLISH.whisperCode)
        assertTrue(SpeechLanguage.entries.none { it.whisperCode == "auto" })
    }

    @Test
    fun peerFilterMatchesNamesIgnoringCase() {
        val open = PeerFilter(restrict = false, allowedNames = emptySet())
        assertTrue(open.allows("Anyone"))
        val closed = PeerFilter(restrict = true, allowedNames = parseAllowlist("Camon 40, Spark 30"))
        assertTrue(closed.allows("spark 30"))
        assertTrue(closed.allows("Camon 40"))
        assertFalse(closed.allows("Other phone"))
        assertFalse(PeerFilter(restrict = true, allowedNames = emptySet()).allows("Spark 30"))
    }

    @Test
    fun endpointBookKeepsPendingAndConnectedApart() {
        val book = RelayEndpoints()
        assertEquals(RelayEndpoints.SessionStart.FRESH, book.beginSession("Camon 40"))
        assertTrue(book.tryBeginConnect("a", "Spark 30"))
        assertFalse(book.tryBeginConnect("a", "Spark 30"))
        assertEquals(1, book.pendingCount())
        book.markConnected("a", "Spark 30", now = 10L)
        assertEquals(0, book.pendingCount())
        assertEquals("Spark 30", book.snapshot().single().name)
        assertTrue(book.snapshot(exceptEndpoint = "a").isEmpty())
        book.markDisconnected("a")
        assertEquals(0, book.connectedCount())
        assertEquals(0, book.pendingCount())
        assertTrue(book.tryBeginConnect("b", "Other"))
    }

    @Test
    fun allowlistBlocksConnect() {
        val book = RelayEndpoints()
        book.beginSession("Camon 40")
        book.setFilter(PeerFilter(true, setOf("Spark 30")))
        assertFalse(book.allows("Other"))
        assertFalse(book.tryBeginConnect("b", "Other"))
        assertEquals(0, book.pendingCount())
        assertTrue(book.tryBeginConnect("c", "spark 30"))
    }

    @Test
    fun summaryUsesModelLineOnlyWhenSane() {
        val rules = "3 trapped, need water, Purok 4"
        val ai = SummaryRefine.choose(rules, "3 trapped, need water, Purok 4")
        assertEquals(SummaryRefine.AI, ai.source)
        assertEquals(rules, ai.summary)
        val refused = SummaryRefine.choose(rules, "I cannot summarize that")
        assertEquals(SummaryRefine.RULES, refused.source)
        assertEquals(rules, refused.summary)
        val empty = SummaryRefine.choose(rules, "   ")
        assertEquals(SummaryRefine.RULES, empty.source)
        val first = SummaryRefine.choose(rules, "Child trapped, need water\nextra")
        assertEquals("Child trapped, need water", first.summary)
        assertEquals(SummaryRefine.AI, first.source)
    }

    @Test
    fun wavHeaderIsPcm16Mono() {
        val file = File.createTempFile("clip", ".wav")
        val samples = FloatArray(160) { 0.5f }
        WavPcm.write(file, samples, sampleRate = 16_000)
        val bytes = file.readBytes()
        assertEquals(WavPcm.HEADER_BYTES + samples.size * 2, bytes.size)
        assertEquals("RIFF", bytes.copyOfRange(0, 4).decodeToString())
        assertEquals("WAVE", bytes.copyOfRange(8, 12).decodeToString())
        val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        header.position(22)
        assertEquals(1, header.short.toInt())
        assertEquals(16_000, header.int)
        header.position(34)
        assertEquals(16, header.short.toInt())
    }
}
