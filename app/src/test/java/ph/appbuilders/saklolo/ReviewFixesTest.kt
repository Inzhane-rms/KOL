package ph.appbuilders.saklolo

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.audio.WavPcm
import java.io.ByteArrayInputStream
import ph.appbuilders.saklolo.relay.ClipRelay
import ph.appbuilders.saklolo.relay.PeerFilter
import ph.appbuilders.saklolo.relay.RelayEndpoints
import ph.appbuilders.saklolo.relay.RelayPermissions
import ph.appbuilders.saklolo.relay.isForegroundStartNotAllowed
import ph.appbuilders.saklolo.relay.parseAllowlist
import ph.appbuilders.saklolo.relay.relayStartFailureMessage
import ph.appbuilders.saklolo.relay.runRelayServiceStart
import ph.appbuilders.saklolo.stt.SpeechLanguage
import ph.appbuilders.saklolo.triage.SummaryRefine
import ph.appbuilders.saklolo.ui.questionToAutoSend
import ph.appbuilders.saklolo.ui.shouldEndSosHold

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
    fun failedRelayStartCanBeTriedAgain() {
        val book = RelayEndpoints()
        assertEquals(RelayEndpoints.SessionStart.FRESH, book.beginSession("Camon 40"))
        assertTrue(book.isRunning())
        assertTrue(book.tryBeginConnect("a", "Spark 30"))
        book.markConnected("a", "Spark 30", now = 10L)
        book.noteStartFailed()
        assertFalse(book.isRunning())
        assertEquals("Spark 30", book.snapshot().single().name)
        assertFalse(book.tryBeginConnect("b", "Other"))
        assertEquals(RelayEndpoints.SessionStart.FRESH, book.beginSession("Camon 40"))
        assertTrue(book.isRunning())
        assertTrue(book.tryBeginConnect("b", "Other"))
    }

    @Test
    fun relayPermissionsGrowWithSdk() {
        val legacy = RelayPermissions.required(26)
        assertEquals(listOf(android.Manifest.permission.ACCESS_FINE_LOCATION), legacy)
        val android12 = RelayPermissions.required(31)
        assertTrue(android12.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(android12.contains(android.Manifest.permission.BLUETOOTH_ADVERTISE))
        assertTrue(android12.contains(android.Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(android12.contains(android.Manifest.permission.BLUETOOTH_CONNECT))
        assertFalse(android12.contains(android.Manifest.permission.NEARBY_WIFI_DEVICES))
        val android13 = RelayPermissions.required(33)
        assertTrue(android13.contains(android.Manifest.permission.NEARBY_WIFI_DEVICES))
        assertTrue(android13.contains(android.Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(android13.contains(android.Manifest.permission.BLUETOOTH_ADVERTISE))
        assertTrue(android13.contains(android.Manifest.permission.BLUETOOTH_CONNECT))
        assertFalse(android13.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
        assertFalse(android13.contains(android.Manifest.permission.RECORD_AUDIO))
    }

    @Test
    fun android13ApproximateLocationStartsTheRelay() {
        assertTrue(RelayPermissions.locationSatisfied(33, fine = false, coarse = true))
        assertTrue(RelayPermissions.locationSatisfied(33, fine = true, coarse = false))
        assertFalse(RelayPermissions.locationSatisfied(33, fine = false, coarse = false))
        assertFalse(RelayPermissions.locationSatisfied(31, fine = false, coarse = true))
        assertFalse(RelayPermissions.locationSatisfied(32, fine = false, coarse = true))
        assertTrue(RelayPermissions.locationSatisfied(32, fine = true, coarse = false))
        assertTrue(RelayPermissions.needsPreciseChoice(31, fine = false, coarse = true))
        assertTrue(RelayPermissions.needsPreciseChoice(32, fine = false, coarse = true))
        assertFalse(RelayPermissions.needsPreciseChoice(33, fine = false, coarse = true))
        assertFalse(RelayPermissions.needsPreciseChoice(31, fine = true, coarse = true))
        assertFalse(RelayPermissions.needsPreciseChoice(31, fine = false, coarse = false))
        assertFalse(RelayPermissions.needsPreciseChoice(26, fine = false, coarse = true))
        assertEquals(
            "Allow location so nearby phones can find this one.",
            RelayPermissions.locationWarning(33, fine = false, coarse = false),
        )
        assertNull(RelayPermissions.locationWarning(33, fine = false, coarse = true))
        assertNull(RelayPermissions.locationWarning(33, fine = true, coarse = false))
        assertEquals(
            "Choose Precise so nearby phones can find this one.",
            RelayPermissions.locationWarning(31, fine = false, coarse = true),
        )
        assertNull(RelayPermissions.locationWarning(31, fine = false, coarse = false))
        assertNull(RelayPermissions.locationWarning(26, fine = false, coarse = false))
    }

    @Test
    fun reconnectSendsOnlyRecentClipsAndPendingWavsAreRemoved() {
        val now = 1_000_000L
        assertTrue(ClipRelay.includeClip(now - 60_000L, now, force = false))
        assertFalse(ClipRelay.includeClip(now - ClipRelay.RECENT_CLIP_MS - 1, now, force = false))
        assertTrue(ClipRelay.includeClip(now - ClipRelay.RECENT_CLIP_MS - 1, now, force = true))
        assertFalse(ClipRelay.includeClip(now + 5_000L, now, force = false))
        val dir = File.createTempFile("clips", "").also { it.delete(); it.mkdirs() }
        val pending = File(dir, "pending-9.wav")
        pending.writeBytes(byteArrayOf(1, 2, 3))
        File(dir, "keep.wav").writeBytes(byteArrayOf(4))
        val waiting = File(dir, "pending-wait.wav")
        waiting.writeBytes(byteArrayOf(5))
        ClipRelay.deletePending(dir, keepNames = setOf("pending-wait.wav"))
        assertFalse(pending.exists())
        assertTrue(waiting.exists())
        assertTrue(File(dir, "keep.wav").exists())
        ClipRelay.deletePending(dir)
        assertFalse(waiting.exists())
        val dest = File(dir, "copied.wav")
        assertTrue(ClipRelay.copyStream(ByteArrayInputStream(byteArrayOf(9, 8, 7)), dest))
        assertEquals(3, dest.length().toInt())
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

    @Test
    fun relayStartFailureDoesNotEscape() {
        var ensured = false
        var failure: Throwable? = null
        val security = runRelayServiceStart(
            startForeground = { throw SecurityException("background") },
            ensureRelay = { ensured = true },
            onFailure = { failure = it },
        )
        assertFalse(security)
        assertFalse(ensured)
        assertTrue(failure is SecurityException)
        assertTrue(relayStartFailureMessage(failure!!).contains("security"))
        assertTrue(relayStartFailureMessage(failure!!).contains("background"))

        failure = null
        val blocked = foregroundStartNotAllowed("not allowed")
        assertTrue(isForegroundStartNotAllowed(blocked))
        val foreground = runRelayServiceStart(
            startForeground = { throw blocked },
            ensureRelay = { error("relay must not start") },
            onFailure = { failure = it },
        )
        assertFalse(foreground)
        assertTrue(relayStartFailureMessage(failure!!).contains("foreground-not-allowed"))

        failure = null
        val other = runRelayServiceStart(
            startForeground = { },
            ensureRelay = { throw IllegalStateException("nearby down") },
            onFailure = { failure = it },
        )
        assertFalse(other)
        assertTrue(relayStartFailureMessage(failure!!).contains("nearby down"))
    }

    @Test
    fun relayStartSuccessLeavesFailureUnset() {
        var foreground = 0
        var ensured = 0
        var failed = false
        val started = runRelayServiceStart(
            startForeground = { foreground++ },
            ensureRelay = { ensured++ },
            onFailure = { failed = true },
        )
        assertTrue(started)
        assertEquals(1, foreground)
        assertEquals(1, ensured)
        assertFalse(failed)
    }

    @Test
    fun slidingOffTheSosButtonDoesNotEndTheHold() {
        assertFalse(shouldEndSosHold(pointerPressed = true, outOfBounds = false, cancelled = false))
        assertFalse(shouldEndSosHold(pointerPressed = true, outOfBounds = true, cancelled = false))
        assertTrue(shouldEndSosHold(pointerPressed = false, outOfBounds = false, cancelled = false))
        assertTrue(shouldEndSosHold(pointerPressed = false, outOfBounds = true, cancelled = false))
        assertTrue(shouldEndSosHold(pointerPressed = true, outOfBounds = true, cancelled = true))
    }

    @Test
    fun topicSeedIsSentRatherThanLeftInTheDraft() {
        assertEquals("nagdudugo", questionToAutoSend("  nagdudugo  "))
        assertEquals("Baha sa bahay, ano ang gagawin?", questionToAutoSend("Baha sa bahay, ano ang gagawin?"))
        assertNull(questionToAutoSend("   "))
        assertNull(questionToAutoSend(null))
    }

    private fun foregroundStartNotAllowed(message: String): Exception {
        val type = Class.forName("android.app.ForegroundServiceStartNotAllowedException")
        val ctor = type.getConstructor(String::class.java)
        return ctor.newInstance(message) as Exception
    }
}
