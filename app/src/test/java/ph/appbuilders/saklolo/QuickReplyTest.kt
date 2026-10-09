package ph.appbuilders.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.contact.ChipTapGuard
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.QuickReplies
import ph.appbuilders.saklolo.contact.ReplyCache
import ph.appbuilders.saklolo.contact.ReplyChannel
import ph.appbuilders.saklolo.contact.ReplyChip
import ph.appbuilders.saklolo.contact.ReplySession

class QuickReplyTest {
    @Test
    fun whereAreYouOffersGateAsAPrefill() {
        listOf("Nasaan ka na?", "Saan ka?").forEach { heard ->
            val chips = QuickReplies.fromRules(heard)
            assertEquals(listOf("Papunta na ako", QuickReplies.GATE_LABEL, "Saan tayo magkita?"), chips.map { it.label })
            val gate = chips[1]
            assertTrue(gate.fill)
            assertEquals(QuickReplies.GATE_PREFILL, gate.sendText)
            assertTrue(chips.filter { it != gate }.none { it.fill })
        }
    }

    @Test
    fun areYouOkAndEmergencyAndGenericStayDistinct() {
        val ok = QuickReplies.fromRules("Ok ka lang ba?")
        assertEquals(listOf("Ok lang ako", "Hindi, tulungan mo ako"), ok.map { it.sendText })
        assertTrue(ok.none { it.fill })

        val emergency = QuickReplies.fromRules("May nahimatay dito")
        assertEquals(listOf("Papunta na ako", "Tatawag ako ng medic"), emergency.map { it.label })
        assertEquals(emergency, QuickReplies.fromRules("Naipit ako sa gate"))
        assertEquals(emergency, QuickReplies.fromRules("Kailangan ng tulong"))

        val generic = QuickReplies.fromRules("Salamat")
        assertEquals(listOf("Sige", "Sandali lang", "Nasaan ka?"), generic.map { it.label })
        assertTrue(QuickReplies.fromRules("   ").isEmpty())
    }

    @Test
    fun modelTextIsUsedOnlyWhenItIsTwoOrThreeShortLines() {
        val accepted = QuickReplies.acceptModel("Papunta na ako\nNandito ako sa Gate __\nSige")
        assertEquals(3, accepted!!.size)
        assertTrue(accepted[1].fill)
        assertEquals(QuickReplies.GATE_PREFILL, accepted[1].sendText)
        assertNull(QuickReplies.acceptModel("only one line"))
        assertNull(QuickReplies.acceptModel(""))
        assertNull(QuickReplies.acceptModel("one\n" + "x".repeat(80)))
        assertNull(QuickReplies.acceptModel("a\nb\nc\nd"))
        val numbered = QuickReplies.acceptModel("1. Sige\n2. Sandali lang")
        assertEquals(listOf("Sige", "Sandali lang"), numbered!!.map { it.label })
    }

    @Test
    fun emergenciesNeverAskTheModel() {
        assertFalse(QuickReplies.mayAskModel("May nahimatay dito"))
        assertFalse(QuickReplies.mayAskModel("Naipit ako sa gate"))
        assertFalse(QuickReplies.mayAskModel("Kailangan ng tulong"))
        assertFalse(QuickReplies.mayAskModel("   "))
        assertTrue(QuickReplies.mayAskModel("Salamat"))
        assertTrue(QuickReplies.mayAskModel("Nasaan ka?"))
    }

    @Test
    fun emergencyChipDropsTheTwoMinuteClaim() {
        val labels = QuickReplies.fromRules("May nahimatay dito").map { it.label }
        assertEquals(listOf("Papunta na ako", "Tatawag ako ng medic"), labels)
        assertFalse(labels.any { it.contains("2 min") })
    }

    @Test
    fun aDoubleTapSendsOneMessage() {
        val guard = ChipTapGuard(windowMs = 600)
        val sent = mutableListOf<String>()
        fun tap(label: String, now: Long) {
            if (guard.allow(now)) sent += label
        }
        tap("Papunta na ako", 1_000)
        tap("Papunta na ako", 1_200)
        tap("Tatawag ako ng medic", 1_400)
        assertEquals(listOf("Papunta na ako"), sent)
        tap("Sige", 1_600)
        assertEquals(listOf("Papunta na ako", "Sige"), sent)
    }

    @Test
    fun chipsHideAfterChipTextOrVoiceAndReturnForANewLine() {
        val session = ReplySession()
        assertEquals("Nasaan ka?", session.offer("ana", "Nasaan ka?"))
        ReplyChannel.entries.forEach { channel ->
            val heard = "line-$channel"
            assertEquals(heard, session.offer("ana", heard))
            session.replied("ana", heard, channel)
            assertEquals("", session.offer("ana", heard))
        }
        assertEquals("Sige na", session.offer("ana", "Sige na"))
        assertEquals("Nasaan ka?", session.offer("ben", "Nasaan ka?"))
    }

    @Test
    fun replyCacheDropsTheLeastRecentlyUsedPast32() {
        val cache = ReplyCache(max = 32)
        val chip = listOf(ReplyChip("Sige", "Sige", fill = false))
        repeat(33) { index -> cache.put("k$index", chip) }
        assertEquals(32, cache.size())
        assertNull(cache.get("k0"))
        assertEquals(chip, cache.get("k1"))
        cache.put("k33", chip)
        assertNull(cache.get("k2"))
        assertEquals(chip, cache.get("k1"))
        assertEquals(32, cache.size())
    }

    @Test
    fun chipsFollowTheLatestReceivedCaption() {
        val messages = listOf(
            message("me", "phone-ana1", "phone-ben1", "Sige", "text"),
            message("them", "phone-ben1", "phone-ana1", "Nasaan ka?", "text"),
            message("ping", "phone-ben1", "phone-ana1", "Ping", "ping"),
        )
        assertEquals("Nasaan ka?", QuickReplies.latestHeard(messages, "phone-ana1"))
        assertEquals("Sige", QuickReplies.latestHeard(messages, "phone-ben1"))
    }

    private fun message(id: String, from: String, to: String, body: String, kind: String) = DirectMessage(
        id = id,
        fromDeviceId = from,
        toDeviceId = to,
        senderName = from,
        body = body,
        createdAtMillis = 1L,
        kind = kind,
    )
}
