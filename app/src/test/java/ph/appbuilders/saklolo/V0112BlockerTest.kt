package ph.appbuilders.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.contact.CallPhase
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.Hangup
import ph.appbuilders.saklolo.contact.Ptt
import ph.appbuilders.saklolo.contact.RingLoop
import ph.appbuilders.saklolo.relay.ReadyToConnect
import ph.appbuilders.saklolo.relay.SetupFacts
import ph.appbuilders.saklolo.relay.SetupKey

class V0112BlockerTest {
    @Test
    fun relayStartsWithoutNameOrNotificationsAndLocationOffBlocksIt() {
        val ready = facts()
        assertTrue(ReadyToConnect.shouldStart(ready))
        assertEquals(0, ReadyToConnect.requiredMissing(ready))
        assertEquals("Ready · 0 in range", ReadyToConnect.statusPill(0, 0))
        assertEquals("Ready · 2 in range", ReadyToConnect.statusPill(0, 2))

        val quiet = ready.copy(notifications = false)
        assertTrue(ReadyToConnect.shouldStart(quiet))
        assertTrue(ReadyToConnect.permissionLine(quiet).contains("notifications=0"))
        assertTrue(ReadyToConnect.permissionLine(quiet).contains("location=1"))

        val locationOff = ready.copy(locationOn = false)
        assertFalse(ReadyToConnect.shouldStart(locationOff))
        assertEquals(1, ReadyToConnect.requiredMissing(locationOff))
        assertEquals("Setup needed", ReadyToConnect.statusPill(1, 0))
        assertTrue(ReadyToConnect.permissionLine(locationOff).contains("location=0"))

        val wifiOff = ready.copy(wifiOn = false, microphone = false, camera = false, batteryUnrestricted = false)
        assertTrue(ReadyToConnect.shouldStart(wifiOff))
        assertEquals(0, ReadyToConnect.requiredMissing(wifiOff))
        val wifi = ReadyToConnect.rows(wifiOff).first { it.key == SetupKey.WIFI }
        assertFalse(wifi.required)
        assertEquals(ReadyToConnect.WIFI_DETAIL, wifi.detail)
    }

    @Test
    fun checklistShowsOnFirstLaunchWhenRequiredIsMissingAndFromThePill() {
        assertTrue(ReadyToConnect.show(seen = false, requiredMissing = 0, pillTapped = false))
        assertFalse(ReadyToConnect.show(seen = true, requiredMissing = 2, pillTapped = false))
        assertTrue(ReadyToConnect.show(seen = true, requiredMissing = 0, pillTapped = true))
        assertFalse(ReadyToConnect.show(seen = true, requiredMissing = 0, pillTapped = false))

        val required = ReadyToConnect.rows(facts()).filter { it.required }.map { it.key }
        assertEquals(
            listOf(SetupKey.BLUETOOTH, SetupKey.LOCATION, SetupKey.NEARBY),
            required,
        )
        assertTrue(SetupKey.WIFI in ReadyToConnect.rows(facts()).filter { !it.required }.map { it.key })
        val recommended = ReadyToConnect.rows(facts().copy(microphone = false, notifications = false))
            .filter { !it.required && !it.ok }
            .map { it.key }
        assertTrue(SetupKey.MICROPHONE in recommended)
        assertTrue(SetupKey.NOTIFICATIONS in recommended)
    }

    @Test
    fun backOnIncomingSendsOneDeclineAndDoesNotReRing() {
        val loop = RingLoop(now = 1_000)
        val invite = invite("inv", 1_000)
        assertTrue(loop.absorb(listOf(invite), ME))
        assertFalse(loop.absorb(listOf(invite), ME))
        assertEquals(CallPhase.INCOMING, loop.call.phase)
        loop.end(listOf(invite), ME)
        assertEquals(CallPhase.IDLE, loop.call.phase)
        assertEquals(listOf(Ptt.DECLINE), kinds(loop))
        assertFalse(loop.absorb(listOf(invite, text("after", 2_000)), ME))
        assertEquals(CallPhase.IDLE, loop.call.phase)
        assertTrue(kinds(loop).isEmpty())
    }

    @Test
    fun ringTimeoutSendsOneDecline() {
        val loop = RingLoop(now = 1_000)
        val invite = invite("inv", 1_000)
        assertTrue(loop.absorb(listOf(invite), ME))
        val token = loop.armToken()
        loop.now = 1_000 + Hangup.RING_MS - 1
        assertFalse(loop.due(token))
        assertFalse(loop.fireTimeout(listOf(invite), ME))
        loop.now = 1_000 + Hangup.RING_MS
        assertTrue(loop.fireTimeout(listOf(invite), ME))
        assertEquals(listOf(Ptt.DECLINE), kinds(loop))
        assertFalse(loop.absorb(listOf(invite), ME))
        loop.now += Hangup.RING_MS
        assertFalse(loop.fireTimeout(listOf(invite), ME))
        assertTrue(kinds(loop).isEmpty())
    }

    @Test
    fun callerCancelWhileRingingDoesNotReRing() {
        val loop = RingLoop(now = 5_000)
        val invite = invite("inv", 5_000)
        assertTrue(loop.absorb(listOf(invite), ME))
        val decline = signal("dec", Ptt.DECLINE, 6_000)
        assertFalse(loop.absorb(listOf(invite, decline), ME))
        assertEquals(CallPhase.IDLE, loop.call.phase)
        assertTrue(kinds(loop).isEmpty())
        assertFalse(loop.absorb(listOf(invite, decline, text("after", 7_000)), ME))
        assertEquals(CallPhase.IDLE, loop.call.phase)
        assertTrue(kinds(loop).isEmpty())
    }

    @Test
    fun callerHangingUpWhileOutgoingSendsOneDecline() {
        val loop = RingLoop(now = 3_000)
        loop.inviteOut(BEN, "Ben")
        assertEquals(CallPhase.OUTGOING, loop.call.phase)
        loop.end(emptyList(), ME)
        assertEquals(CallPhase.IDLE, loop.call.phase)
        assertEquals(listOf(Ptt.DECLINE), kinds(loop))
        loop.now = 3_000 + Hangup.RING_MS
        assertFalse(loop.fireTimeout(emptyList(), ME))
        assertTrue(kinds(loop).isEmpty())
    }

    private fun kinds(loop: RingLoop): List<String> = loop.drain().map { it.kind }

    private fun invite(id: String, at: Long) = DirectMessage(
        id = id,
        fromDeviceId = BEN,
        toDeviceId = ME,
        senderName = "Ben",
        body = "Call",
        createdAtMillis = at,
        kind = Ptt.INVITE,
    )

    private fun signal(id: String, kind: String, at: Long) = DirectMessage(
        id = id,
        fromDeviceId = BEN,
        toDeviceId = ME,
        senderName = "Ben",
        body = kind,
        createdAtMillis = at,
        kind = kind,
    )

    private fun text(id: String, at: Long) = DirectMessage(
        id = id,
        fromDeviceId = BEN,
        toDeviceId = ME,
        senderName = "Ben",
        body = "hello",
        createdAtMillis = at,
        kind = "text",
    )

    private fun facts() = SetupFacts(
        bluetoothOn = true,
        locationOn = true,
        nearbyPermission = true,
        wifiOn = true,
        microphone = true,
        camera = true,
        notifications = true,
        batteryUnrestricted = true,
    )

    private companion object {
        const val ME = "phone-ana01"
        const val BEN = "phone-ben01"
    }
}
