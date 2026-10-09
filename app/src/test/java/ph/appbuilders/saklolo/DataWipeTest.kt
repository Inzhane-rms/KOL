package ph.appbuilders.saklolo

import java.io.File
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.contact.ClipCommit
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.DirectStore
import ph.appbuilders.saklolo.contact.MemoryDirectPersistence
import ph.appbuilders.saklolo.contact.ReplyCache
import ph.appbuilders.saklolo.contact.ReplyChip
import ph.appbuilders.saklolo.contact.WipeLaunch
import ph.appbuilders.saklolo.contact.WipeSessions
import ph.appbuilders.saklolo.data.SentAtMigration
import ph.appbuilders.saklolo.group.GroupNote
import ph.appbuilders.saklolo.group.GroupPersistence
import ph.appbuilders.saklolo.group.GroupSnapshot
import ph.appbuilders.saklolo.group.GroupStore
import ph.appbuilders.saklolo.group.VoiceDraft
import ph.appbuilders.saklolo.relay.OutboundGate
import ph.appbuilders.saklolo.stt.ModelInstaller
import ph.appbuilders.saklolo.ui.messageClock

/** Delete all data clears the phone and leaves the speech model file. */
class DataWipeTest {
    @Test
    fun wipeStopsTheRelayThenClearsRoomClipsPrefsAndTheReplyCache() {
        val order = mutableListOf<String>()
        val direct = DirectStore(MemoryDirectPersistence())
        direct.myId = "phone-ana-1111"
        direct.saveQr("phone-ben-2222", "Ben", 1_000)
        direct.addLocal(
            DirectMessage(
                id = "m1",
                fromDeviceId = "phone-ana-1111",
                toDeviceId = "phone-ben-2222",
                senderName = "Ana",
                body = "Nandito ako",
                createdAtMillis = 2_000,
            ),
        )
        val replies = ReplyCache()
        replies.put("nandito", listOf(ReplyChip("Sige", "Sige", fill = false)))
        val dir = File.createTempFile("wipe", "").apply {
            delete()
            mkdirs()
        }
        File(dir, "note.m4a").writeBytes(byteArrayOf(1, 2, 3))
        File(dir, UserDataErase.MODEL_FILE).writeBytes(byteArrayOf(9))
        File(dir, "gemma3-1b-it-int4.task").writeBytes(byteArrayOf(8))
        var deviceId = "phone-ana-1111"
        val next = UserDataErase.run(
            stopRelay = { order += "relay" },
            clearRoom = {
                order += "room"
                direct.wipe()
            },
            clearClips = {
                order += "clips"
                dir.listFiles()?.forEach { file ->
                    if (!UserDataErase.keep(file.name)) file.delete()
                }
            },
            clearPrefs = {
                order += "prefs"
                deviceId = ""
            },
            clearReplies = {
                order += "replies"
                replies.clear()
            },
            newDeviceId = {
                order += "device"
                deviceId = "phone-new-3333"
                deviceId
            },
        )
        assertEquals(listOf("relay", "room", "clips", "prefs", "replies", "device"), order)
        assertEquals("phone-new-3333", next)
        assertTrue(direct.rows().isEmpty())
        assertTrue(direct.visible("phone-new-3333").isEmpty())
        assertEquals(0, replies.size())
        assertFalse(File(dir, "note.m4a").exists())
        assertTrue(File(dir, UserDataErase.MODEL_FILE).exists())
        assertTrue(File(dir, "gemma3-1b-it-int4.task").exists())
        assertTrue(UserDataErase.keep(UserDataErase.MODEL_FILE))
        assertFalse(UserDataErase.keep("note.m4a"))
        dir.deleteRecursively()
    }

    @Test
    fun legalCopyMatchesTheRelayAndDoesNotClaimCompliance() {
        val privacy = asset("privacy.txt")
        val safety = asset("safety.txt")
        val licenses = asset("licenses.txt")
        val strings = stringsXml()
        assertTrue(privacy.contains("Philippine Data Privacy Act of 2012 (RA 10173)"))
        assertFalse(privacy.contains("compliant", ignoreCase = true))
        assertFalse(privacy.contains("compliance", ignoreCase = true))
        assertTrue(privacy.contains("may pass through other KOL phones"))
        assertTrue(privacy.contains("Messages are not end-to-end encrypted, so don't send anything sensitive."))
        assertTrue(privacy.contains("Settings > Delete all data"))
        assertFalse(privacy.contains("directly phone to phone"))
        assertTrue(safety.contains("call 911 when you have signal."))
        assertFalse(licenses.contains("whisper-small-tagalog"))
        assertFalse(licenses.contains("RNNoise"))
        assertTrue(licenses.contains("Gemma"))
        assertTrue(licenses.contains("3.4 GiB"))
        assertTrue(licenses.contains("Poppins"))
        assertTrue(strings.contains("I agree"))
        assertTrue(strings.contains("Delete all data"))
        assertTrue(strings.contains(">Delete</string>"))
        assertTrue(strings.contains("Terms of use"))
        assertTrue(strings.contains("Privacy policy"))
        assertTrue(strings.contains("By continuing you agree to the Terms of use and Privacy policy"))
        assertTrue(strings.contains("This can\\'t be undone."))
        assertTrue(strings.contains("Messages you already sent stay on the other person\\'s phone."))
        assertTrue(strings.contains(">Cancel<"))
        assertFalse(strings.contains("Delivered"))
        val theme = themeXml()
        assertTrue(theme.contains("@drawable/ic_launcher_foreground"))
        assertFalse(theme.contains("ic_kol_tile"))
        val clock = messageClock(1_700_000_000_000L)
        assertTrue(clock.contains(":"))
        assertFalse(clock.contains("delivered", ignoreCase = true))
    }

    @Test
    fun aStaleClipNeverSavesOrSendsAfterTheWipeBump() {
        val store = DirectStore(MemoryDirectPersistence())
        store.myId = "phone-ana-1111"
        val started = WipeSessions(hold = 2, epoch = 4, voice = 3, record = 1)
        val live = started.bump()
        assertFalse(ClipCommit.allow(started.hold, live.hold))
        assertFalse(ClipCommit.allow(started.voice, live.voice))
        assertTrue(VoiceDraft.decide(started.epoch, live.epoch, "nandito ako") is VoiceDraft.Finish.Discarded)
        val epoch = store.epoch()
        val gate = OutboundGate()
        val token = gate.token()
        gate.cancel()
        assertFalse(gate.live(token))
        assertTrue(gate.live(gate.token()))
        val stale = DirectMessage(
            id = "stale",
            fromDeviceId = "phone-ana-1111",
            toDeviceId = "phone-ben-2222",
            senderName = "Ana",
            body = "Nandito ako",
            createdAtMillis = 5L,
            kind = "call_clip",
        )
        if (ClipCommit.allow(started.hold, live.hold) && gate.live(token)) {
            store.addIfCurrent(stale, epoch)
        }
        assertTrue(store.visible(store.myId).isEmpty())
        store.bumpEpoch()
        assertNull(store.addIfCurrent(stale, epoch))
        val fresh = store.addIfCurrent(stale.copy(id = "fresh"), store.epoch())
        assertEquals("fresh", fresh!!.id)
        assertEquals(0L, store.find("fresh")!!.sentAtMillis)
        assertTrue(store.markSent(listOf("fresh"), 80L))
        assertEquals(80L, store.find("fresh")!!.sentAtMillis)
        assertFalse(store.markSent(listOf("fresh"), 90L))
        assertFalse(store.markSent(listOf("fresh"), 0L))
        assertEquals(80L, store.find("fresh")!!.sentAtMillis)
    }

    @Test
    fun roomWritesStayOnTheCallerSoMainThreadQueriesStay() {
        val saver = object : GroupPersistence {
            var thread: Thread? = null
            override fun load(): GroupSnapshot = GroupSnapshot()
            override fun save(snapshot: GroupSnapshot) {
                thread = Thread.currentThread()
            }
        }
        val store = GroupStore(saver)
        store.addLocal(GroupNote(id = "n1", groupId = "g1", sender = "Ana", body = "Ping", createdAtMillis = 1L))
        assertEquals(Thread.currentThread(), saver.thread)
        val runtime = listOf(
            File("src/main/java/ph/appbuilders/saklolo/SakloloRuntime.kt"),
            File("app/src/main/java/ph/appbuilders/saklolo/SakloloRuntime.kt"),
        ).first { it.exists() }.readText()
        assertTrue(runtime.contains("allowMainThreadQueries()"))
    }

    @Test
    fun smallTagalogModelNeedsARealDigestAndIsNotTheDefault() {
        assertEquals("", ModelInstaller.SMALL_TL_SHA256)
        assertFalse(ModelInstaller.acceptsSmall(60L * 1024 * 1024, "ab".repeat(32)))
        val digest = "cd".repeat(32)
        assertEquals(64, digest.length)
        assertTrue(ModelInstaller.acceptsSmall(60L * 1024 * 1024, digest, expected = digest))
        assertTrue(ModelInstaller.acceptsSmall(60L * 1024 * 1024, digest.uppercase(), expected = digest))
        assertFalse(ModelInstaller.acceptsSmall(49L * 1024 * 1024, digest, expected = digest))
        assertFalse(ModelInstaller.acceptsSmall(60L * 1024 * 1024, "ab".repeat(32), expected = digest))
        assertFalse(ModelInstaller.acceptsSmall(60L * 1024 * 1024, digest, expected = ""))
        val source = listOf(
            File("src/main/java/ph/appbuilders/saklolo/stt/ModelInstaller.kt"),
            File("app/src/main/java/ph/appbuilders/saklolo/stt/ModelInstaller.kt"),
        ).first { it.exists() }.readText()
        assertTrue(source.contains("fun resolve(context: Context): File = installedFile(context)"))
    }

    @Test
    fun deleteDoesNotBlockWhileTheRecorderLockIsHeld() {
        val gate = Mutex()
        assertTrue(gate.tryLock())
        var stopped = false
        val started = System.nanoTime()
        val ran = WipeLaunch.tryStop(gate) { stopped = true }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertFalse(ran)
        assertFalse(stopped)
        assertTrue("tryStop waited ${elapsedMs}ms while the recorder lock was held", elapsedMs < 50)
        gate.unlock()
        assertTrue(WipeLaunch.tryStop(gate) { stopped = true })
        assertTrue(stopped)
        val source = viewModelSource()
        assertFalse(source.contains("runBlocking"))
        assertTrue(source.contains("viewModelScope.launch"))
        assertTrue(source.contains("withContext(Dispatchers.IO)"))
        assertTrue(source.contains("WipeLaunch.tryStop"))
    }

    @Test
    fun endCallGoesOutBeforeTheRelayStops() {
        val duringCall = mutableListOf<String>()
        WipeLaunch.finishCallThenStop(
            callActive = true,
            sendEnd = { duringCall += "end" },
            waitForSend = { duringCall += "wait" },
            stopRelay = { duringCall += "stop" },
        )
        assertEquals(listOf("end", "wait", "stop"), duringCall)
        val idle = mutableListOf<String>()
        WipeLaunch.finishCallThenStop(
            callActive = false,
            sendEnd = { idle += "end" },
            waitForSend = { idle += "wait" },
            stopRelay = { idle += "stop" },
        )
        assertEquals(listOf("stop"), idle)
        assertEquals(500L, WipeLaunch.END_CALL_WINDOW_MS)
        val source = viewModelSource()
        val send = source.indexOf("broadcastDirect(message)")
        val wait = source.indexOf("awaitOutbound(WipeLaunch.END_CALL_WINDOW_MS)")
        val stop = source.indexOf("runtime.wipeUserData")
        assertTrue(send in 0 until wait)
        assertTrue(wait < stop)
    }

    @Test
    fun migrationMarksExistingOutgoingRowsSent() {
        val sql = mutableListOf<String>()
        SentAtMigration.migrate { sql += it }
        assertEquals(listOf(SentAtMigration.ADD_COLUMN, SentAtMigration.MARK_OUTGOING), sql)
        assertTrue(SentAtMigration.MARK_OUTGOING.contains("sentAtMillis = createdAtMillis"))
        assertTrue(SentAtMigration.MARK_OUTGOING.contains("localOrigin != 0"))
        assertEquals(4_000L, SentAtMigration.sentAt(localOrigin = true, createdAtMillis = 4_000L))
        assertEquals(0L, SentAtMigration.sentAt(localOrigin = false, createdAtMillis = 4_000L))
    }

    private fun asset(name: String): String {
        val file = listOf(
            File("src/main/assets/legal/$name"),
            File("app/src/main/assets/legal/$name"),
        ).first { it.exists() }
        return file.readText()
    }

    private fun stringsXml(): String {
        val file = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml"),
        ).first { it.exists() }
        return file.readText()
    }

    private fun viewModelSource(): String {
        val file = listOf(
            File("src/main/java/ph/appbuilders/saklolo/SakloloViewModel.kt"),
            File("app/src/main/java/ph/appbuilders/saklolo/SakloloViewModel.kt"),
        ).first { it.exists() }
        return file.readText()
    }

    private fun themeXml(): String {
        val file = listOf(
            File("src/main/res/values-v31/themes.xml"),
            File("app/src/main/res/values-v31/themes.xml"),
        ).first { it.exists() }
        return file.readText()
    }
}
