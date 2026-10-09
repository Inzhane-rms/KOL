package ph.appbuilders.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.appbuilders.saklolo.contact.DirectMessage
import ph.appbuilders.saklolo.contact.QuickReplies

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
        assertEquals(listOf("Papunta na ako, 2 min", "Tatawag ako ng medic"), emergency.map { it.label })
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
