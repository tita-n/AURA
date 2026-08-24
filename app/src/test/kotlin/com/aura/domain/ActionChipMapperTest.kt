package com.aura.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * Regression tests: ACTION chips must carry validated communication targets.
 * Physical-device bug — tapping Message gave "No matches" (dropped phone -> Unavailable),
 * tapping Call gave "Invalid contact" (contactId constructed into phoneNumber slot).
 */
class ActionChipMapperTest {

    private val parentMessage = ResolvedResult(
        id = "contact:c1", title = "Dad", subtitle = "phone",
        type = ResultType.Message,
        action = AuraAction.SendMessage("c1", channel = "default", phone = "+10000000001"),
        actionChips = listOf(ActionChipData("message", "Message"), ActionChipData("call", "Call"))
    )

    private val parentCall = ResolvedResult(
        id = "contact:c1", title = "Dad", type = ResultType.Call,
        action = AuraAction.Dial("+10000000001", contactId = "c1")
    )

    @Test fun `message chip from message parent preserves phone`() {
        val mapped = ActionChipMapper.map(parentMessage, ActionChipData("message", "Message"))
        val a = (mapped as ResolvedResult).action as AuraAction.SendMessage
        assertEquals("+10000000001", a.phone)
        assertEquals("c1", a.contactId)
    }

    @Test fun `call chip keeps real phone - not contactId in number slot`() {
        val mapped = ActionChipMapper.map(parentMessage, ActionChipData("call", "Call")) as ResolvedResult
        val dial = mapped.action as AuraAction.Dial
        assertEquals("+10000000001", dial.phoneNumber)
        assertEquals("c1", dial.contactId)
    }

    @Test fun `message chip from call parent keeps phone`() {
        val mapped = ActionChipMapper.map(parentCall, ActionChipData("message", "Message")) as ResolvedResult
        val sms = mapped.action as AuraAction.SendMessage
        assertEquals("+10000000001", sms.phone)
        assertEquals("c1", sms.contactId)
    }

    @Test fun `email chip carries address when present`() {
        val emailParent = ResolvedResult(
            id = "contact:c3", title = "Sarah", type = ResultType.Email,
            action = AuraAction.SendEmail("c3", emailAddress = "sarah.a@example.com")
        )
        val mapped = ActionChipMapper.map(emailParent, ActionChipData("email", "Email")) as ResolvedResult
        assertEquals("sarah.a@example.com", (mapped.action as AuraAction.SendEmail).emailAddress)
    }

    @Test fun `unknown chip never invents an action`() {
        assertNull(ActionChipMapper.map(parentMessage, ActionChipData("teleport", "Teleport")))
    }

    @Test fun `mapped results keep L3-relevant fields intact`() {
        // Type switches with chip; title/subtitle/id stay stable so UI is continuous
        val mapped = ActionChipMapper.map(parentMessage, ActionChipData("call", "Call")) as ResolvedResult
        assertEquals(ResultType.Call, mapped.type)
        assertEquals("Dad", mapped.title)
        assertEquals("contact:c1", mapped.id)
    }
}
