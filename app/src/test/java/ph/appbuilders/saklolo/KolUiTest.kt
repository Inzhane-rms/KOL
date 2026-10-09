package ph.appbuilders.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.contact.ContactRow
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.HoldMute
import ph.appbuilders.saklolo.ui.KolNav
import ph.appbuilders.saklolo.ui.MainNav

class KolUiTest {
    @Test
    fun homeIsTheStartAndTheBarHidesOnChatAndCall() {
        assertEquals(MainNav.HOME, MainNav.tabs.first())
        assertEquals(listOf("home", "contacts", "messages", "add"), MainNav.tabs)
        assertFalse(MainNav.tabs.any { it.equals("sos", ignoreCase = true) })
        assertTrue(KolNav.showsBar(MainNav.HOME))
        assertTrue(KolNav.showsBar(MainNav.CONTACTS))
        assertFalse(KolNav.showsBar(MainNav.THREAD))
        assertFalse(KolNav.showsBar(MainNav.CALL))
    }

    @Test
    fun tabsSlideAndPushesComeFromTheRight() {
        assertEquals(220, KolNav.MOTION_MS)
        assertTrue(KolNav.slideFromRight(MainNav.HOME, MainNav.CONTACTS))
        assertFalse(KolNav.slideFromRight(MainNav.ADD, MainNav.HOME))
        assertTrue(KolNav.slideFromRight(MainNav.MESSAGES, MainNav.THREAD))
        assertTrue(KolNav.slideFromRight(MainNav.HOME, MainNav.CALL))
        assertFalse(KolNav.slideFromRight(MainNav.CALL, MainNav.HOME))
        assertFalse(KolNav.slideFromRight(MainNav.THREAD, MainNav.MESSAGES))
    }

    @Test
    fun eachTabKeepsItsOwnStateKey() {
        assertEquals("home", KolNav.stateKey(MainNav.HOME, ""))
        assertEquals("contacts", KolNav.stateKey(MainNav.CONTACTS, "ana"))
        assertEquals("thread:phone-ben", KolNav.stateKey(MainNav.THREAD, "phone-ben"))
        assertEquals("thread:", KolNav.stateKey(MainNav.THREAD, ""))
        assertFalse(KolNav.stateKey(MainNav.CONTACTS, "") == KolNav.stateKey(MainNav.MESSAGES, ""))
    }

    @Test
    fun codeAndActivityUseRealRowsOnly() {
        assertEquals("KOL-AB12", KolNav.kolCode("BLNK · AB12"))
        assertEquals("Setup needed", KolNav.homeBackLabel(1, 3, 50L, 100L))
        assertEquals("1 queued", KolNav.homeBackLabel(0, 1, 50L, 100L))
        val me = "phone-ana1"
        val ben = contact("phone-ben1", "Ben", saved = true, inRange = false, added = 5_000)
        val messages = listOf(
            message("v1", "phone-ben1", me, "Nandito ako", "voice", 2_000),
            message("t1", me, "phone-ben1", "Sige", "text", 3_000, local = true),
        )
        val activity = KolNav.recentActivity(messages, listOf(ben), me)
        assertEquals(
            listOf("Contact added · Ben", "Waiting · Ben", "Voice note · Ben"),
            activity.map { it.title },
        )
        assertEquals("waiting", activity.first { it.peerId == "phone-ben1" && it.at == 3_000L }.tone)
        assertTrue(activity.none { it.title.contains("delivered", ignoreCase = true) })
        assertTrue(activity.none { it.title.contains("missed", ignoreCase = true) })
        val delivered = KolNav.recentActivity(messages, listOf(ben.copy(inRange = true)), me)
        assertEquals("Message delivered · Ben", delivered.first { it.at == 3_000L }.title)
        assertEquals("Offline · 1 friend in range", KolNav.friendsLine(1))
        assertEquals("Offline · 2 friends in range", KolNav.friendsLine(2))
        assertEquals(1, KolNav.waitingPeers(messages, listOf(ben), me))
        assertEquals(0, KolNav.waitingPeers(messages, listOf(ben.copy(inRange = true)), me))
        assertFalse(HoldMute.allowStart(muted = true))
        assertTrue(HoldMute.allowStart(muted = false))
        assertTrue(HoldMute.dropInFlight(muted = true))
        assertFalse(HoldMute.dropInFlight(muted = false))
    }

    private fun contact(id: String, name: String, saved: Boolean, inRange: Boolean, added: Long) = ContactRow(
        deviceId = id,
        name = name,
        favorite = false,
        saved = saved,
        inRange = inRange,
        lastHeardMillis = added,
        addedAtMillis = added,
    )

    private fun message(id: String, from: String, to: String, body: String, kind: String, at: Long, local: Boolean = false) =
        DirectMessage(
            id = id,
            fromDeviceId = from,
            toDeviceId = to,
            senderName = from,
            body = body,
            createdAtMillis = at,
            kind = kind,
            localOrigin = local,
        )
}
