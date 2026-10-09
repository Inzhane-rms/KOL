package ph.appbuilders.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import ph.appbuilders.saklolo.contact.CallClock
import ph.appbuilders.saklolo.contact.CallSession
import ph.appbuilders.saklolo.contact.ContactRow
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.HoldMute
import ph.appbuilders.saklolo.ui.KolCopy
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
        assertEquals("In range · Ben", delivered.first { it.at == 3_000L }.title)
        assertEquals("Offline · 1 friend in range", KolNav.friendsLine(1))
        assertEquals("Offline · 2 friends in range", KolNav.friendsLine(2))
        assertEquals(1, KolNav.waitingPeers(messages, listOf(ben), me))
        assertEquals(0, KolNav.waitingPeers(messages, listOf(ben.copy(inRange = true)), me))
        assertFalse(HoldMute.allowStart(muted = true))
        assertTrue(HoldMute.allowStart(muted = false))
        assertTrue(HoldMute.dropInFlight(muted = true))
        assertFalse(HoldMute.dropInFlight(muted = false))
        assertTrue(HoldMute.keep(true))
        assertFalse(HoldMute.keep(false))
        assertFalse(HoldMute.afterEnd())
    }

    @Test
    fun callReturnsToThatChatAndTheTimerStartsAtZero() {
        assertEquals(MainNav.THREAD, KolNav.backTarget(MainNav.THREAD, MainNav.HOME))
        assertEquals(MainNav.HOME, KolNav.backTarget(MainNav.HOME, MainNav.CONTACTS))
        assertEquals(MainNav.CONTACTS, KolNav.backTarget(MainNav.CALL, MainNav.CONTACTS))
        assertEquals(MainNav.THREAD, KolNav.afterCall("phone-ben"))
        assertEquals(MainNav.HOME, KolNav.afterCall(" "))
        assertEquals(MainNav.HOME, KolNav.afterCall(""))
        assertEquals(1L, CallSession.nextId(wasIdle = true, nowIdle = false, currentId = 0L))
        assertEquals(1L, CallSession.nextId(wasIdle = false, nowIdle = false, currentId = 1L))
        assertEquals(2L, CallSession.nextId(wasIdle = true, nowIdle = false, currentId = 1L))
        assertEquals(0L, CallSession.nextId(wasIdle = false, nowIdle = true, currentId = 1L))
        assertEquals(0L, CallClock.anchor(active = false, callId = 1L, previousAnchor = 50L, now = 100L))
        assertEquals(100L, CallClock.anchor(active = true, callId = 2L, previousAnchor = 0L, now = 100L))
        assertEquals(50L, CallClock.anchor(active = true, callId = 2L, previousAnchor = 50L, now = 100L))
        assertEquals("0:00", CallClock.label(0L))
        assertEquals("0:00", CallClock.label(-20L))
        assertEquals("1:05", CallClock.label(65_000L))
    }

    @Test
    fun navPillStaysAt120AndTheCopyHasNoOldName() {
        assertEquals(120, KolNav.navPillWidth())
        assertEquals("Hold to talk", KolNav.HOLD_TO_TALK)
        assertTrue(KolCopy.LABELS.none { it == "Talk" || it.contains("Tap to talk") || it.contains("Push to talk") })
        assertTrue(KolCopy.LABELS.none { it.contains("B-LINK") || it.contains("Saklolo") })
        assertTrue(KolCopy.LABELS.none { it.contains("Delivered") })
        val strings = stringsXml()
        assertFalse(strings.contains("B-LINK"))
        assertFalse(strings.contains("Saklolo"))
        assertTrue(strings.contains(">KOL<"))
    }

    private fun stringsXml(): String {
        val candidates = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml"),
        )
        val file = candidates.firstOrNull { it.exists() } ?: error("strings.xml missing")
        return file.readText()
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
