package ph.appbuilders.saklolo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.DirectStore
import ph.appbuilders.saklolo.contact.MemoryDirectPersistence
import ph.appbuilders.saklolo.contact.ReplyCache
import ph.appbuilders.saklolo.contact.ReplyChip
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

    private fun themeXml(): String {
        val file = listOf(
            File("src/main/res/values-v31/themes.xml"),
            File("app/src/main/res/values-v31/themes.xml"),
        ).first { it.exists() }
        return file.readText()
    }
}
