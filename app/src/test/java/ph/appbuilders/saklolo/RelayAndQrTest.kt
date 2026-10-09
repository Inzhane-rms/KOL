package ph.appbuilders.saklolo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.model.AlertJson
import ph.appbuilders.saklolo.model.AlertStore
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
        val reloaded = AlertStore(file).snapshot().map { it.id }
        assertEquals(order, reloaded)
    }

    @Test
    fun hopLimitStopsForwarding() {
        val origin = alert("a", Urgency.CRITICAL, hops = 0, at = 1)
        assertEquals(0, RelayPolicy.outgoing(origin, localOrigin = true)?.hops)
        assertEquals(1, RelayPolicy.outgoing(origin, localOrigin = false)?.hops)
        assertEquals(5, RelayPolicy.outgoing(origin.copy(hops = 4), localOrigin = false)?.hops)
        assertNull(RelayPolicy.outgoing(origin.copy(hops = 5), localOrigin = false))

        val store = AlertStore(File.createTempFile("hops", ".json"))
        assertTrue(store.ingest(listOf(origin.copy(hops = 6))).isEmpty())
        assertEquals(1, store.ingest(listOf(origin.copy(id = "edge", hops = 5))).size)
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
    fun envelopeRoundTrip() {
        val alerts = listOf(alert("x", Urgency.NEEDS_HELP, hops = 1, at = 9))
        val decoded = AlertJson.decodeEnvelope(AlertJson.encodeEnvelope(alerts))
        assertEquals(alerts, decoded)
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
