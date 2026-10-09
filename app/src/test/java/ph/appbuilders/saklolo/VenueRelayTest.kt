package ph.appbuilders.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.contact.Ack
import ph.appbuilders.saklolo.contact.CallOffer
import ph.appbuilders.saklolo.contact.ContactQr
import ph.appbuilders.saklolo.contact.Delivery
import ph.appbuilders.saklolo.contact.DirectGate
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.DirectStore
import ph.appbuilders.saklolo.contact.EndpointCard
import ph.appbuilders.saklolo.contact.Held
import ph.appbuilders.saklolo.contact.HoldPolicy
import ph.appbuilders.saklolo.contact.LinkMessage
import ph.appbuilders.saklolo.contact.MemoryDirectPersistence
import ph.appbuilders.saklolo.contact.MeshPhone
import ph.appbuilders.saklolo.contact.RadioPolicy
import ph.appbuilders.saklolo.contact.RelayCatalog
import ph.appbuilders.saklolo.contact.SealedBox
import ph.appbuilders.saklolo.contact.SendGate
import ph.appbuilders.saklolo.contact.SummarySync
import ph.appbuilders.saklolo.contact.toDirect
import ph.appbuilders.saklolo.contact.toWire
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.model.AlertJson
import ph.appbuilders.saklolo.relay.RelayLane
import ph.appbuilders.saklolo.relay.RelayPolicy
import ph.appbuilders.saklolo.triage.Urgency

class VenueRelayTest {
    @Test
    fun sealRoundTripAndARelayCannotReadOrForgeIt() {
        val alice = SealedBox.generate()
        val bob = SealedBox.generate()
        val eve = SealedBox.generate()
        val alicePub = SealedBox.encodeKey(alice.publicKey)
        val bobPub = SealedBox.encodeKey(bob.publicKey)
        val draft = DirectMessage(
            id = "msg-1",
            fromDeviceId = "phone-alic1",
            toDeviceId = "phone-bob01",
            senderName = "Ana",
            body = "Nasa stage left ako",
            createdAtMillis = 50,
            kind = "text",
        )
        val sealed = LinkMessage.sealText(alice.privateKey, bobPub, draft)!!
        val wire = sealed.toWire().toDirect()
        assertEquals("", wire.body)
        assertEquals("", wire.senderName)
        assertTrue(wire.box.isNotBlank())
        val opened = LinkMessage.openText(bob.privateKey, alicePub, wire)!!
        assertEquals("Ana", opened.senderName)
        assertEquals("Nasa stage left ako", opened.body)
        assertNull(LinkMessage.openText(eve.privateKey, alicePub, wire))
        val flipped = wire.copy(box = wire.box.dropLast(1) + if (wire.box.last() == 'A') 'B' else 'A')
        assertNull(LinkMessage.openText(bob.privateKey, alicePub, flipped))
        val wav = "RIFF-voice".toByteArray()
        val clip = LinkMessage.sealClip(alice.privateKey, bobPub, sealed, wav)!!
        assertFalse(wav.contentEquals(clip))
        assertTrue(wav.contentEquals(LinkMessage.openClip(bob.privateKey, alicePub, sealed, clip)))
        assertNull(LinkMessage.openClip(eve.privateKey, alicePub, sealed, clip))
    }

    @Test
    fun relayForwardsAStrangerAndDedupes() {
        val ana = MeshPhone("phone-ana01", "Ana")
        val relay = MeshPhone("phone-relay1", "Relay")
        val ben = MeshPhone("phone-ben01", "Ben")
        ana.connect(relay, 1_000)
        relay.connect(ben, 1_100)
        val sent = ana.send(ben, "across the room", at = 2_000)
        assertEquals("across the room", ben.store.visible(ben.deviceId).single().body)
        assertTrue(relay.store.rows().none { it.deviceId == ana.deviceId && it.saved })
        assertTrue(DirectGate.shouldForward(relay.store.find(sent.id)!!, relay.deviceId))
        assertFalse(DirectGate.show(relay.store.find(sent.id)!!, relay.deviceId))
        relay.accept(sent, ana, 2_100)
        assertEquals(1, relay.store.snapshot().messages.count { it.id == sent.id })
    }

    @Test
    fun hopLimitDropsTheSixthHop() {
        val phones = (0..6).map { MeshPhone("phone-hop$it-xx", "P$it") }
        for (index in 0 until phones.lastIndex) {
            phones[index].connect(phones[index + 1], 1_000L + index)
        }
        phones[0].send(phones[6], "far", at = 9_000)
        assertTrue(phones[5].store.snapshot().messages.any { it.body == "far" })
        assertTrue(phones[6].store.snapshot().messages.none { it.body == "far" })
        val tooFar = DirectMessage(
            id = "late",
            fromDeviceId = "phone-hop0-xx",
            toDeviceId = "phone-hop6-xx",
            senderName = "P0",
            body = "nope",
            createdAtMillis = 1,
            hops = RelayPolicy.MAX_HOPS,
        )
        assertNull(DirectGate.receive(tooFar))
    }

    @Test
    fun ttlAndCapsDropOldestNonSosFirst() {
        val expired = Held("old", 0, 1, 0, sos = false)
        val fresh = Held("new", 10, HoldPolicy.expireAt(10), 0, sos = false)
        assertEquals(listOf("new"), HoldPolicy.evict(listOf(expired, fresh), now = 5_000).map { it.id })

        val crowded = (0 until 201).map { index ->
            Held("m$index", index.toLong(), HoldPolicy.expireAt(index.toLong()), 0, sos = index == 0)
        }
        val kept = HoldPolicy.evict(crowded, now = 0)
        assertEquals(HoldPolicy.MAX_MESSAGES, kept.size)
        assertTrue(kept.any { it.id == "m0" })
        assertFalse(kept.any { it.id == "m1" })

        val clips = listOf(
            Held("sos", 1, HoldPolicy.expireAt(1), 15_000_000, sos = true),
            Held("a", 2, HoldPolicy.expireAt(2), 10_000_000, sos = false),
            Held("b", 3, HoldPolicy.expireAt(3), 10_000_000, sos = false),
        )
        val clipped = HoldPolicy.evict(clips, now = 0).map { it.id }
        assertEquals(listOf("sos"), clipped)

        val onlySos = listOf(Held("huge", 1, HoldPolicy.expireAt(1), HoldPolicy.MAX_CLIP_BYTES + 1, sos = true))
        assertTrue(HoldPolicy.evict(onlySos, now = 0).isEmpty())

        val sosAlert = Alert(
            id = "sos-1",
            transcript = "May nasaktan",
            summary = "Someone is hurt",
            urgency = Urgency.CRITICAL,
            createdAtMillis = 20,
        )
        val chat = DirectMessage(
            id = "chat-1",
            fromDeviceId = "phone-ana01",
            toDeviceId = "phone-ben01",
            senderName = "Ana",
            body = "hi",
            createdAtMillis = 10,
            localOrigin = false,
            clipBytes = HoldPolicy.MAX_CLIP_BYTES,
            expireAtMillis = HoldPolicy.expireAt(10),
        )
        val ids = RelayCatalog.ids(listOf(chat), listOf(sosAlert), now = 30) { HoldPolicy.MAX_CLIP_BYTES }
        assertEquals(setOf("sos-1"), ids)
    }

    @Test
    fun summaryVectorSendsOnlyMissingIdsAndIsRateLimited() {
        val mine = setOf("a", "b", "c")
        val theirs = setOf("b", "d")
        assertEquals(setOf("a", "c"), SummarySync.theyNeed(mine, theirs))
        assertFalse(SummarySync.allow(1_000, 1_000 + SummarySync.MIN_INTERVAL_MS - 1))
        assertTrue(SummarySync.allow(1_000, 1_000 + SummarySync.MIN_INTERVAL_MS))
        assertEquals(RelayLane.SOS, RelayLane.next(hasSos = true, hasLive = true, hasHistory = true))
        assertEquals(RelayLane.LIVE, RelayLane.next(hasSos = false, hasLive = true, hasHistory = true))
        assertEquals(RelayLane.HISTORY, RelayLane.next(hasSos = false, hasLive = false, hasHistory = true))
    }

    @Test
    fun deliveredOnlyAfterTheAck() {
        val alice = SealedBox.generate()
        val bob = SealedBox.generate()
        val alicePub = SealedBox.encodeKey(alice.publicKey)
        val bobPub = SealedBox.encodeKey(bob.publicKey)
        val sender = DirectStore(MemoryDirectPersistence())
        sender.myId = "phone-alic1"
        sender.rememberKey("phone-bob01", bobPub, "Ben")
        val draft = DirectMessage(
            id = "msg-ack",
            fromDeviceId = "phone-alic1",
            toDeviceId = "phone-bob01",
            senderName = "Ana",
            body = "Ping text",
            createdAtMillis = 80,
        )
        val sealed = LinkMessage.sealText(alice.privateKey, bobPub, draft)!!
        sender.addLocal(sealed)
        assertEquals("Sending", Delivery.label(sender.find(sealed.id)!!.delivery, 0))
        sender.markRelayed(sealed.id, 1)
        assertEquals("Relayed · 1 hops", Delivery.label(sender.find(sealed.id)!!.delivery, sender.find(sealed.id)!!.relayHops))
        assertNotEquals(Delivery.DELIVERED, sender.find(sealed.id)!!.delivery)

        val recipient = DirectStore(MemoryDirectPersistence())
        recipient.myId = "phone-bob01"
        recipient.rememberKey("phone-alic1", alicePub, "Ana")
        recipient.openSealed = { LinkMessage.openText(bob.privateKey, recipient.publicKey(it.fromDeviceId), it) }
        val got = recipient.ingest(listOf(sealed.toWire().toDirect()), 90).single()
        assertEquals("Ping text", got.body)
        val ack = LinkMessage.ack(got, "phone-bob01", "Ben", bob.privateKey, alicePub, 100)!!
        assertEquals("", ack.toWire().body)
        sender.openSealed = { LinkMessage.openText(alice.privateKey, sender.publicKey(it.fromDeviceId), it) }
        sender.ingest(listOf(ack.toWire().toDirect()), 110)
        val done = sender.find(sealed.id)!!
        assertEquals(Delivery.DELIVERED, done.delivery)
        assertEquals("Delivered", Delivery.label(done.delivery, done.relayHops))
        assertEquals(sealed.id, Ack.parse(LinkMessage.openText(alice.privateKey, bobPub, ack.toWire().toDirect())!!.body)!!.first)
    }

    @Test
    fun callFallbackAndKeyGateAndRadioFloor() {
        assertEquals("Not in direct range. Send a voice note instead?", CallOffer.FALLBACK)
        assertEquals(CallOffer.FALLBACK, CallOffer.fallback(directlyConnected = false))
        assertNull(CallOffer.fallback(directlyConnected = true))
        assertEquals(SendGate.NEED_KEY, SendGate.blockReason(hasPeerKey = false, sos = false))
        assertNull(SendGate.blockReason(hasPeerKey = false, sos = true))
        assertNull(SendGate.blockReason(hasPeerKey = true, sos = false))
        assertFalse(RadioPolicy.advertise(batteryPercent = 9, sosInFlight = false))
        assertTrue(RadioPolicy.advertise(batteryPercent = 9, sosInFlight = true))
        assertTrue(RadioPolicy.advertise(batteryPercent = 10, sosInFlight = false))
        assertEquals(100, ph.appbuilders.saklolo.relay.BatteryLevel.percent(-1, 100))
        assertEquals(9, ph.appbuilders.saklolo.relay.BatteryLevel.percent(9, 100))
    }

    @Test
    fun qrCarriesThePublicKeyAndSosStaysPlaintext() {
        val keys = SealedBox.generate()
        val pub = SealedBox.encodeKey(keys.publicKey)
        val encoded = ContactQr.encode("phone-ana01", "Ana|Cruz", pub)
        val parsed = ContactQr.decode(encoded)!!
        assertEquals("phone-ana01", parsed.deviceId)
        assertEquals("Ana|Cruz", parsed.name)
        assertEquals(pub, parsed.publicKey)
        assertEquals("BLKC|1|phone-ana01|Ana", ContactQr.encode("phone-ana01", "Ana"))
        val card = EndpointCard.encode("phone-ana01", "A very long display name that should shrink", pub)
        assertTrue(card.endsWith(pub))
        assertTrue(card.length <= 120)
        val alert = Alert(
            id = "sos-plain",
            transcript = "May nasaktan sa gate",
            summary = "Someone is hurt",
            urgency = Urgency.CRITICAL,
            createdAtMillis = 4,
        )
        val packet = AlertJson.decodeEnvelope(AlertJson.encodeEnvelope(listOf(alert), summary = listOf("sos-plain")))
        assertEquals("May nasaktan sa gate", packet.alerts.single().transcript)
        assertEquals(listOf("sos-plain"), packet.summary)
    }
}
