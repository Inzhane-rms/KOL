package ph.appbuilders.saklolo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.audio.Waveform
import ph.appbuilders.saklolo.audio.WavPcm
import ph.appbuilders.saklolo.contact.CallMachine
import ph.appbuilders.saklolo.contact.CallPhase
import ph.appbuilders.saklolo.contact.ContactQr
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.DirectStore
import ph.appbuilders.saklolo.contact.MemoryDirectPersistence
import ph.appbuilders.saklolo.contact.MeshPhone
import ph.appbuilders.saklolo.contact.Ptt
import ph.appbuilders.saklolo.contact.Urgent
import ph.appbuilders.saklolo.relay.ReadyToConnect
import ph.appbuilders.saklolo.relay.SetupFacts
import ph.appbuilders.saklolo.ui.ModelDisclosure

/** JVM walk of the live chat, call, and setup paths. Phones are not in this run. */
class PathCheckTest {
    @Test
    fun addContactTextBothWaysVoiceCallEmergencyAndUrgent() {
        val ana = MeshPhone("phone-ana1", "Ana")
        val ben = MeshPhone("phone-ben1", "Ben")
        val card = ContactQr.decode(ContactQr.encode(ben.deviceId, ben.name))!!
        ana.store.saveQr(card.deviceId, card.name, 1_000)
        assertTrue(ana.store.rows().single().saved)

        ana.connect(ben, 1_100)
        ana.send(ben, "Nasaan ka?", at = 2_000)
        ben.send(ana, "Nandito ako", at = 2_100)
        assertEquals("Nandito ako", ana.store.thread(ana.deviceId, ben.deviceId).last().body)
        assertEquals("Nasaan ka?", ben.store.thread(ben.deviceId, ana.deviceId).first().body)

        ana.send(ben, "Dito sa gate", kind = "voice", at = 3_000, audioPath = "/clips/note.wav")
        assertEquals("voice", ben.store.thread(ben.deviceId, ana.deviceId).last().kind)

        ana.call = CallMachine.inviteOut(ben.deviceId, ben.name)
        ana.send(ben, "Call", kind = Ptt.INVITE, at = 4_000)
        assertEquals(CallPhase.INCOMING, ben.call.phase)
        ben.call = CallMachine.accept(ben.call)
        ben.send(ana, "Accept", kind = Ptt.ACCEPT, at = 4_100)
        assertEquals(CallPhase.ACTIVE, ana.call.phase)

        val words = "May nahimatay dito"
        ana.send(ben, words, kind = Ptt.CLIP, at = 5_000)
        assertTrue(Ptt.emergency(words))
        assertEquals(words, ben.store.thread(ben.deviceId, ana.deviceId).last().body)
        assertTrue(ben.played.isNotEmpty())

        ana.send(ben, words, kind = Urgent.KIND, at = 6_000)
        val urgent = ben.store.thread(ben.deviceId, ana.deviceId).last()
        assertEquals(Urgent.KIND, urgent.kind)
        assertTrue(ben.store.conversations(ben.deviceId, emptyMap()).single { it.peerId == ana.deviceId }.urgent)
    }

    @Test
    fun messagesStayAfterARestart() {
        val disk = MemoryDirectPersistence()
        val first = DirectStore(disk)
        first.myId = "phone-ana1"
        first.saveQr("phone-ben1", "Ben", 1_000)
        first.addLocal(
            DirectMessage(
                id = "m1",
                fromDeviceId = "phone-ana1",
                toDeviceId = "phone-ben1",
                senderName = "Ana",
                body = "Nandito ako",
                createdAtMillis = 2_000,
            ),
        )
        val restored = DirectStore(disk)
        restored.myId = "phone-ana1"
        assertEquals("Ben", restored.rows().single().name)
        assertEquals("Nandito ako", restored.visible("phone-ana1").single().body)
    }

    @Test
    fun permissionTogglesGateTheRelayAndTheReadyScreen() {
        val ready = facts(bluetooth = true, location = true, nearby = true)
        assertTrue(ReadyToConnect.shouldStart(ready))
        assertFalse(ReadyToConnect.shouldStart(ready.copy(bluetoothOn = false)))
        assertFalse(ReadyToConnect.shouldStart(ready.copy(locationOn = false)))
        assertFalse(ReadyToConnect.shouldStart(ready.copy(nearbyPermission = false)))
        assertTrue(ReadyToConnect.shouldStart(ready.copy(wifiOn = false)))
        assertTrue(ReadyToConnect.show(seen = false, requiredMissing = 2, pillTapped = false))
        assertFalse(ReadyToConnect.show(seen = true, requiredMissing = 2, pillTapped = false))
        assertTrue(ReadyToConnect.show(seen = true, requiredMissing = 2, pillTapped = true))
        assertEquals("Setup needed", ReadyToConnect.statusPill(1, 0))
    }

    @Test
    fun waveformReadsWavAndRoutesM4aToAac() {
        val dir = File.createTempFile("wave", "").apply {
            delete()
            mkdirs()
        }
        val wav = File(dir, "clip.wav")
        val loud = ShortArray(1600) { index -> if (index > 800) 16000 else 100 }
        WavPcm.write(wav, FloatArray(loud.size) { loud[it] / 32767f })
        assertEquals(Waveform.WAV, Waveform.decoder(wav))
        val bars = Waveform.bars(wav, 4)
        assertEquals(4, bars.size)
        assertTrue(bars.last() > bars.first())
        val cached = Waveform.bars(wav, 4)
        assertEquals(bars, cached)

        val m4a = File(dir, "clip.m4a")
        m4a.writeBytes(ByteArray(16) { 1 })
        assertEquals(Waveform.AAC, Waveform.decoder(m4a))
        assertTrue(WavPcm.peakBars(m4a).isEmpty())
        dir.deleteRecursively()
    }

    @Test
    fun aboutScreenNamesWhisperGemmaAndTheRulesEngine() {
        assertTrue(ModelDisclosure.mentions("Whisper"))
        assertTrue(ModelDisclosure.mentions("Gemma 3 1B"))
        assertTrue(ModelDisclosure.mentions("rules engine"))
        assertTrue(ModelDisclosure.mentions("no internet"))
        val manifest = File("src/main/AndroidManifest.xml")
        if (manifest.exists()) {
            val text = manifest.readText()
            assertTrue(text.contains("android.permission.INTERNET"))
            assertTrue(text.contains("tools:node=\"remove\""))
        }
    }

    private fun facts(bluetooth: Boolean, location: Boolean, nearby: Boolean) = SetupFacts(
        bluetoothOn = bluetooth,
        locationOn = location,
        nearbyPermission = nearby,
        wifiOn = true,
        microphone = true,
        camera = true,
        notifications = true,
        batteryUnrestricted = true,
    )
}
