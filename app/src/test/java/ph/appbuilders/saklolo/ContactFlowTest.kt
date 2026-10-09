package ph.appbuilders.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.contact.CallMachine
import ph.appbuilders.saklolo.contact.CallPhase
import ph.appbuilders.saklolo.contact.CallState
import ph.appbuilders.saklolo.contact.CaptionDisplay
import ph.appbuilders.saklolo.contact.ContactQr
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.DirectStore
import ph.appbuilders.saklolo.contact.Identity
import ph.appbuilders.saklolo.contact.MemoryDirectPersistence
import ph.appbuilders.saklolo.contact.MeshPhone
import ph.appbuilders.saklolo.contact.OriginalCaption
import ph.appbuilders.saklolo.contact.Ptt
import ph.appbuilders.saklolo.contact.ResyncPlan
import ph.appbuilders.saklolo.contact.VoiceControl
import ph.appbuilders.saklolo.contact.toDirect
import ph.appbuilders.saklolo.contact.toWire
import ph.appbuilders.saklolo.group.VoiceSheet
import ph.appbuilders.saklolo.model.AlertJson
import ph.appbuilders.saklolo.triage.Urgency

class ContactFlowTest {
    @Test
    fun contactQrRoundTripAndRejectsGroupCodes() {
        val id = "11111111-2222-3333-4444-555555555555"
        val encoded = ContactQr.encode(id, "Ana Reyes")
        assertEquals("BLKC|1|$id|Ana Reyes", encoded)
        assertEquals(id, ContactQr.decode("  $encoded  ")?.deviceId)
        assertEquals("Ana Reyes", ContactQr.decode(java.net.URLEncoder.encode(encoded, "UTF-8"))?.name)
        assertEquals("Ana|Reyes", ContactQr.decode(ContactQr.encode(id, "Ana|Reyes"))?.name)
        assertNull(ContactQr.decode("BLK1|1|$id|Barkada"))
        assertNull(ContactQr.decode("BLKC|9|$id|Ana"))
        assertNull(ContactQr.decode("BLKC|1|short|Ana"))
        assertNull(ContactQr.decode(""))
    }

    @Test
    fun mineIsDeviceIdNotDisplayName() {
        val me = "device-me-0001"
        val other = "device-ot-0002"
        assertTrue(Identity.isMine(me, me, localOrigin = false))
        assertTrue(Identity.isMine(other, me, localOrigin = true))
        assertFalse(Identity.isMine(other, me, localOrigin = false))
        assertEquals("Pixel 7a 4f2a", Identity.defaultName("Pixel 7a", "4f2a9999-0000-0000-0000-000000000000"))
        assertFalse(Identity.defaultName("Pixel 7a", "4f2a9999-0000-0000-0000-000000000000") == "Me")
    }

    @Test
    fun onlyTheAddressedPhoneShowsARelayedMessage() {
        val ana = MeshPhone("phone-ana1", "Ana")
        val ben = MeshPhone("phone-ben1", "Ben")
        val dee = MeshPhone("phone-dee1", "Dee")
        ana.connect(dee, 1_000)
        dee.connect(ben, 1_100)
        ana.send(ben, "Nasa stage left ako", at = 2_000)
        assertEquals(listOf("Nasa stage left ako"), ben.store.visible(ben.deviceId).map { it.body })
        assertTrue(dee.store.visible(dee.deviceId).none { it.body == "Nasa stage left ako" })
        assertTrue(dee.store.relayHistory().any { it.body == "Nasa stage left ako" })
        assertTrue(ben.store.visible(ben.deviceId).single().hops >= 1)
    }

    @Test
    fun resyncGoesToTheNewPeerWithoutClipsAndLiveGoesFirst() {
        val history = listOf(
            DirectMessage("m1", "phone-ana1", "phone-ben1", "Ana", "hi", 1, audioPath = "/tmp/clip.wav"),
        )
        val plan = ResyncPlan.history("endpoint-new", history)
        assertEquals("endpoint-new", plan.endpointId)
        assertFalse(plan.attachClips)
        assertNull(plan.messages.single().audioPath)
        val ordered = ResyncPlan.liveBeforeHistory(
            listOf(plan, ResyncPlan.live(listOf("endpoint-new"), history.single(), withClip = true).single()),
        )
        assertTrue(ordered.first().live)
        assertFalse(ordered.last().live)
    }

    @Test
    fun pttClipTriageAndCaptionStayTheTranscript() {
        val heard = "May nahimatay dito sa Gate 3!"
        assertEquals(heard, CaptionDisplay.text("  $heard  "))
        assertFalse(CaptionDisplay.text(heard).contains("fainted"))
        assertFalse(CaptionDisplay.text(heard).contains("Someone"))
        assertTrue(Ptt.emergency(heard))
        assertEquals(Urgency.CRITICAL, Ptt.triageCaption(heard).urgency)
        var state = CallState()
        state = CallMachine.inviteIn(state, "phone-ana1", "Ana")
        assertEquals(CallPhase.INCOMING, state.phase)
        state = CallMachine.accept(state)
        assertEquals(CallPhase.ACTIVE, state.phase)
        assertFalse(state.speakerOn)
        assertTrue(CallMachine.canHold(state))
        assertFalse(CallMachine.canHold(state.copy(playing = true)))
        state = CallMachine.remoteSignal(
            state,
            DirectMessage("end", "phone-ana1", "phone-me01", "Ana", "End", 3, kind = Ptt.END),
            "phone-me01",
        )
        assertEquals(CallPhase.IDLE, state.phase)
    }

    @Test
    fun contactStorePersistsFavoritesAndLastHeardByDevice() {
        val memory = MemoryDirectPersistence()
        val store = DirectStore(memory)
        store.myId = "phone-me01"
        store.saveQr("phone-ana1", "Ana", 10)
        store.setFavorite("phone-ana1", true)
        store.notePeer("phone-ana1", "Ana Reyes", 50)
        store.addLocal(
            DirectMessage("t1", "phone-me01", "phone-ana1", "Me Phone", "hi", 60),
        )
        val reloaded = DirectStore(memory)
        val row = reloaded.rows().single()
        assertEquals("phone-ana1", row.deviceId)
        assertTrue(row.favorite)
        assertTrue(row.saved)
        assertEquals(50, row.lastHeardMillis)
        assertEquals("t1", reloaded.visible("phone-me01").single().id)
    }

    @Test
    fun eightStepFlowOnAThreePhoneMesh() {
        val ana = MeshPhone("phone-ana1", "Ana")
        val ben = MeshPhone("phone-ben1", "Ben")
        val dee = MeshPhone("phone-dee1", "Dee")

        ana.connect(ben, 1_000)
        assertTrue(ana.store.rows().any { it.deviceId == ben.deviceId && it.inRange })

        val card = ContactQr.decode(ContactQr.encode(dee.deviceId, dee.name))!!
        ana.store.saveQr(card.deviceId, card.name, 1_500)
        assertTrue(ana.store.rows().any { it.deviceId == dee.deviceId && it.saved })

        ben.connect(dee, 1_600)
        ana.send(ben, "Nasaan ka?", at = 2_000)
        ben.send(ana, "Dito sa barrier", at = 2_100)
        assertEquals("Dito sa barrier", ana.store.thread(ana.deviceId, ben.deviceId).last().body)
        assertTrue(dee.store.visible(dee.deviceId).none { it.body == "Nasaan ka?" })

        ana.call = CallMachine.inviteOut(ben.deviceId, ben.name)
        ana.send(ben, "Call", kind = Ptt.INVITE, at = 3_000)
        assertEquals(CallPhase.INCOMING, ben.call.phase)
        ben.call = CallMachine.accept(ben.call)
        ben.send(ana, "Accept", kind = Ptt.ACCEPT, at = 3_100)
        assertEquals(CallPhase.ACTIVE, ana.call.phase)

        val words = "May nahimatay dito sa Gate 3!"
        ana.send(ben, words, kind = Ptt.CLIP, at = 4_000)
        assertEquals(words, ben.store.thread(ben.deviceId, ana.deviceId).last().body)
        assertEquals(words, CaptionDisplay.text(ben.store.thread(ben.deviceId, ana.deviceId).last().body))
        assertTrue(Ptt.emergency(words))
        assertTrue(ben.played.isNotEmpty())

        ana.send(ben, "Dito ako", kind = "voice", at = 5_000, audioPath = "/clips/note.wav")
        val voice = ben.store.thread(ben.deviceId, ana.deviceId).last()
        assertEquals("voice", voice.kind)
        assertEquals("Dito ako", voice.body)

        ana.send(ben, words, kind = ph.appbuilders.saklolo.contact.Urgent.KIND, at = 5_500)
        val urgent = ben.store.thread(ben.deviceId, ana.deviceId).last()
        assertEquals(ph.appbuilders.saklolo.contact.Urgent.KIND, urgent.kind)
        assertTrue(urgent.body.contains("nahimatay"))
        assertTrue(ben.store.conversations(ben.deviceId, emptyMap()).single { it.peerId == ana.deviceId }.urgent)

        val disk = MemoryDirectPersistence()
        val saved = DirectStore(disk)
        saved.saveQr(ben.deviceId, ben.name, 6_000)
        saved.addLocal(DirectMessage("keep", ana.deviceId, ben.deviceId, ana.name, "Dito ako", 6_100, kind = "voice"))
        val restored = DirectStore(disk)
        assertEquals("Dito ako", restored.visible(ana.deviceId).single().body)
        assertTrue(restored.rows().single().saved)
    }

    @Test
    fun cancelStopsOnlyTheLiveRecorderSession() {
        assertTrue(VoiceControl.shouldStopRecorder(4, 4, recorderRunning = true))
        assertFalse(VoiceControl.shouldStopRecorder(4, 5, recorderRunning = true))
        assertFalse(VoiceControl.shouldStopRecorder(4, 4, recorderRunning = false))
        val newer = VoiceSheet(session = 5, recording = true, status = "Recording", transcript = "")
        val kept = ph.appbuilders.saklolo.group.VoiceDraft.afterDiscard(4, newer)
        assertTrue(kept.recording)
        assertEquals(5, kept.session)
    }

    @Test
    fun directEnvelopeRoundTrip() {
        val message = DirectMessage(
            id = "abc",
            fromDeviceId = "phone-ana1",
            toDeviceId = "phone-ben1",
            senderName = "Ana",
            body = "Nahimatay si Ana.",
            createdAtMillis = 9,
            kind = "text",
            rawBody = "nahimatay si ana",
            sentAtMillis = 42L,
        )
        val encoded = AlertJson.encodeEnvelope(emptyList(), direct = listOf(message.toWire()))
        val decoded = AlertJson.decodeEnvelope(encoded).direct.single().toDirect()
        assertEquals("phone-ben1", decoded.toDeviceId)
        assertEquals("Nahimatay si Ana.", decoded.body)
        assertEquals("nahimatay si ana", decoded.rawBody)
        assertEquals(0L, decoded.sentAtMillis)
        assertEquals("Original: nahimatay si ana", OriginalCaption.line(decoded.body, decoded.rawBody))
        assertEquals(null, OriginalCaption.line("Hi", "hi"))
        assertEquals(null, OriginalCaption.line("Hi", "  "))
    }
}
