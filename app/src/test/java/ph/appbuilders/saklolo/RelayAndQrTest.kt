package ph.appbuilders.saklolo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.audio.WavPcm
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.ui.alertProgress
import ph.appbuilders.saklolo.model.AlertJson
import ph.appbuilders.saklolo.model.AlertStore
import ph.appbuilders.saklolo.model.ClipLink
import ph.appbuilders.saklolo.relay.QrCodec
import ph.appbuilders.saklolo.relay.RelayPolicy
import ph.appbuilders.saklolo.triage.Urgency

class RelayAndQrTest {
    @Test
    fun dedupesAndSortsCriticalFirst() {
        val file = File.createTempFile("alerts", ".json")
        val store = AlertStore(file)
        store.ingest(listOf(alert("safe", Urgency.SAFE, hops = 0, at = 2)))
        store.ingest(listOf(alert("help", Urgency.NEEDS_HELP, hops = 1, at = 3)))
        store.ingest(listOf(alert("crit", Urgency.CRITICAL, hops = 2, at = 1)))
        assertTrue(store.ingest(listOf(alert("crit", Urgency.CRITICAL, hops = 2, at = 1))).isEmpty())
        val order = store.snapshot().map { it.id }
        assertEquals(listOf("crit", "help", "safe"), order)
        val reloaded = AlertStore(file).snapshot()
        assertEquals(order, reloaded.map { it.id })
        assertEquals(3, reloaded.first { it.id == "crit" }.hops)
    }

    @Test
    fun hopLimitStopsForwarding() {
        val origin = alert("a", Urgency.CRITICAL, hops = 0, at = 1)
        assertEquals(0, RelayPolicy.outgoing(origin)?.hops)
        assertEquals(4, RelayPolicy.outgoing(origin.copy(hops = 4))?.hops)
        assertNull(RelayPolicy.outgoing(origin.copy(hops = 5)))

        val store = AlertStore(File.createTempFile("hops", ".json"))
        assertTrue(store.ingest(listOf(origin.copy(hops = 6))).isEmpty())
        assertTrue(store.ingest(listOf(origin.copy(id = "five", hops = 5))).isEmpty())
        val edge = store.ingest(listOf(origin.copy(id = "edge", hops = 4)))
        assertEquals(5, edge.single().hops)
    }

    @Test
    fun directReceiptShowsOneHop() {
        val file = File.createTempFile("direct", ".json")
        val store = AlertStore(file)
        val fresh = store.ingest(listOf(alert("direct", Urgency.CRITICAL, hops = 0, at = 5)))
        assertEquals(1, fresh.single().hops)
        assertEquals(1, store.snapshot().single().hops)
        assertEquals(1, AlertStore(file).snapshot().single().hops)
        store.addLocal(alert("local", Urgency.SAFE, hops = 0, at = 6))
        assertEquals(0, store.find("local")!!.hops)
    }

    @Test
    fun qrRoundTripKeepsSummaryAndUrgency() {
        val original = alert("id-1", Urgency.CRITICAL, hops = 2, at = 1_700_000_000_000).copy(
            summary = "3 trapped, need water, Purok 4",
            lat = 14.59951,
            lon = 120.98422,
        )
        val decoded = QrCodec.decode(QrCodec.encode(original))
        assertEquals(original.id, decoded?.id)
        assertEquals(original.summary, decoded?.summary)
        assertEquals(Urgency.CRITICAL, decoded?.urgency)
        assertEquals(2, decoded?.hops)
        assertEquals(14.59951, decoded?.lat!!, 0.00001)
        assertEquals(120.98422, decoded.lon!!, 0.00001)
    }

    @Test
    fun envelopeRoundTripLeavesAudioOffTheWire() {
        val alerts = listOf(
            alert("x", Urgency.NEEDS_HELP, hops = 1, at = 9).copy(
                summarySource = "AI",
                audioPath = "/secret/clip.wav",
                deliveredCount = 3,
                responding = true,
            ),
        )
        val encoded = AlertJson.encodeEnvelope(alerts, listOf(ClipLink("x", 42L)))
        assertFalse(encoded.contains("audioPath"))
        assertFalse(encoded.contains("/secret"))
        assertFalse(encoded.contains("deliveredCount"))
        assertFalse(encoded.contains("responding"))
        val decoded = AlertJson.decodeEnvelope(encoded)
        assertEquals("AI", decoded.alerts.single().summarySource)
        assertNull(decoded.alerts.single().audioPath)
        assertEquals(0, decoded.alerts.single().deliveredCount)
        assertFalse(decoded.alerts.single().responding)
        assertEquals(42L, decoded.clips.single().payloadId)
        assertEquals(
            alerts.single().copy(audioPath = null, deliveredCount = 0, responding = false),
            decoded.alerts.single(),
        )
    }

    @Test
    fun respondingStaysOnThisPhone() {
        val file = File.createTempFile("respond", ".json")
        val store = AlertStore(file)
        store.addLocal(alert("a", Urgency.CRITICAL, hops = 0, at = 1))
        store.setResponding("a", true)
        assertTrue(store.find("a")!!.responding)
        assertTrue(AlertStore(file).find("a")!!.responding)
        val encoded = AlertJson.encodeEnvelope(listOf(store.find("a")!!))
        assertFalse(encoded.contains("responding"))
        assertFalse(AlertJson.decodeEnvelope(encoded).alerts.single().responding)
    }

    @Test
    fun queuedSendIsSentNotDelivered() {
        assertEquals(false, alertProgress(localOrigin = true, deliveredCount = 0).sent)
        assertEquals(true, alertProgress(localOrigin = true, deliveredCount = 0).recorded)
        val queued = alertProgress(localOrigin = true, deliveredCount = 2)
        assertEquals(true, queued.sent)
        assertEquals(false, queued.delivered)
        assertEquals(false, alertProgress(localOrigin = false, deliveredCount = 1).recorded)
    }

    @Test
    fun waveformFollowsTheClip() {
        val dir = File.createTempFile("clip", ".dir")
        dir.delete()
        dir.mkdirs()
        val file = WavPcm.clipFile(dir, "a")
        val samples = FloatArray(16_000) { index -> if (index < 8_000) 0.8f else 0.1f }
        WavPcm.write(file, samples)
        val bars = WavPcm.peakBars(file, 4)
        assertEquals(4, bars.size)
        assertTrue(bars[0] > bars[3])
        assertEquals("0:01", WavPcm.durationLabel(file))
        assertTrue(WavPcm.peakBars(File(dir, "missing.wav")).isEmpty())
    }

    private fun alert(id: String, urgency: Urgency, hops: Int, at: Long) = Alert(
        id = id,
        transcript = "transcript $id",
        summary = "summary $id",
        urgency = urgency,
        createdAtMillis = at,
        hops = hops,
    )
}
