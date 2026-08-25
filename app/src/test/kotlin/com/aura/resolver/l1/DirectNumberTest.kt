package com.aura.resolver.l1

import com.aura.domain.*
import com.aura.resolver.L0IndexFactory
import com.aura.resolver.IntentRouter
import com.aura.resolver.L0Resolver
import org.junit.Assert.*
import org.junit.Test

/**
 * Direct (contactless) number/address actions:
 *   "call 555 123 4567"      -> Dial to that number
 *   "message +15551234567 hi" -> sms composer with preserved body
 *   "email someone@example.com" -> mailto composer
 *
 * Safety: targets must pass deterministic shape validation; contact path unchanged;
 * whatsapp-to-non-contact stays unsupported.
 */
class DirectNumberTest {

    private val index = L0IndexFactory.demoIndex()
    private val router = IntentRouter(
        L0Resolver(index),
        L1Resolver(index),
        com.aura.resolver.l2.L2Resolver(index),
        com.aura.resolver.l3.L3Validator(index)
    )

    private fun assertDirect(query: String, check: (ResolvedResult) -> Unit) {
        val out = router.route(query)
        assertTrue("Expected Act for '$query', got $out", out is ResolutionOutcome.Act)
        check((out as ResolutionOutcome.Act).result)
    }

    // ---- CALL ----

    @Test fun `call plus number dials it`() {
        assertDirect("call +15551234567") { r ->
            val dial = r.action as AuraAction.Dial
            assertEquals("+15551234567", dial.phoneNumber)
            assertNull(dial.contactId)
        }
    }

    @Test fun `call spaced number dials it`() {
        assertDirect("call 555 123 4567") { r ->
            assertEquals("555 123 4567", (r.action as AuraAction.Dial).phoneNumber)
        }
    }

    @Test fun `call dashed number dials it`() {
        assertDirect("call 555-123-4567") { r ->
            assertEquals("555-123-4567", (r.action as AuraAction.Dial).phoneNumber)
        }
    }

    // ---- MESSAGE ----

    @Test fun `message plus number opens composer to it`() {
        assertDirect("message +15551234567") { r ->
            val sms = r.action as AuraAction.SendMessage
            assertEquals("+15551234567", sms.phone)
            assertTrue(sms.contactId.isBlank())
        }
    }

    @Test fun `message number with body preserves body`() {
        assertDirect("text 5551234567 running late") { r ->
            val sms = r.action as AuraAction.SendMessage
            assertEquals("5551234567", sms.phone)
            assertEquals("running late", sms.message)
        }
    }

    // ---- EMAIL ----

    @Test fun `email address opens mailto`() {
        assertDirect("email someone@example.com") { r ->
            val mail = r.action as AuraAction.SendEmail
            assertEquals("someone@example.com", mail.emailAddress)
            assertTrue(mail.contactId.isBlank())
        }
    }

    @Test fun `email address with body preserves body`() {
        assertDirect("email someone@example.com quick question") { r ->
            val mail = r.action as AuraAction.SendEmail
            assertEquals("someone@example.com", mail.emailAddress)
            assertEquals("quick question", mail.body)
        }
    }

    // ---- VALIDATION ----

    @Test fun `L3 validates direct dial`() {
        val res = ResolvedResult("dial:x", "+15551234567", type = ResultType.Call,
            action = AuraAction.Dial("+15551234567"))
        assertTrue(com.aura.resolver.l3.L3Validator(index).validate(res)
            is com.aura.resolver.l3.L3ValidationResult.Validated)
    }

    @Test fun `L3 rejects direct dial with letters`() {
        val res = ResolvedResult("dial:x", "call me maybe", type = ResultType.Call,
            action = AuraAction.Dial("call me maybe"))
        assertTrue(com.aura.resolver.l3.L3Validator(index).validate(res)
            is com.aura.resolver.l3.L3ValidationResult.Invalid)
    }

    @Test fun `L3 validates direct sms`() {
        val res = ResolvedResult("sms:x", "555", type = ResultType.Message,
            action = AuraAction.SendMessage("", "default", "hi", phone = "5551234567"))
        assertTrue(com.aura.resolver.l3.L3Validator(index).validate(res)
            is com.aura.resolver.l3.L3ValidationResult.Validated)
    }

    @Test fun `L3 rejects direct whatsapp - cannot message non-contact safely`() {
        val res = ResolvedResult("wa:x", "?", type = ResultType.Message,
            action = AuraAction.SendMessage("", "whatsapp", phone = "5551234567"))
        val v = com.aura.resolver.l3.L3Validator(index).validate(res)
        assertTrue(v is com.aura.resolver.l3.L3ValidationResult.Invalid ||
                   v is com.aura.resolver.l3.L3ValidationResult.Unavailable)
    }

    @Test fun `L3 validates direct email`() {
        val res = ResolvedResult("e:x", "a@b.co", type = ResultType.Email,
            action = AuraAction.SendEmail("", emailAddress = "a@b.co"))
        assertTrue(com.aura.resolver.l3.L3Validator(index).validate(res)
            is com.aura.resolver.l3.L3ValidationResult.Validated)
    }

    @Test fun `L3 rejects malformed direct email`() {
        val res = ResolvedResult("e:x", "not-an-email", type = ResultType.Email,
            action = AuraAction.SendEmail("", emailAddress = "not-an-email"))
        assertTrue(com.aura.resolver.l3.L3Validator(index).validate(res)
            is com.aura.resolver.l3.L3ValidationResult.Invalid)
    }

    // ---- CONTACT PATH UNCHANGED ----

    @Test fun `contact-based call still works and wins over number heuristics`() {
        val out = router.route("call dad")
        val dial = (out as ResolutionOutcome.Act).result.action as AuraAction.Dial
        assertEquals("+2348010000004", dial.phoneNumber)
        assertEquals("4", dial.contactId) // real indexed contact keeps its id
    }

    @Test fun `unknown name still Empty - not treated as number`() {
        assertTrue(router.route("call zzzz") is ResolutionOutcome.Empty)
    }

    @Test fun `no new CommandState`() {
        val allowed = setOf("Idle", "Input", "Act", "Ask", "Empty", "Error")
        listOf("call +15551234567", "message 555 x", "email a@b.co").forEach { q ->
            val cmd = router.route(q).toCommandState()
            assertTrue(cmd::class.simpleName in allowed)
        }
    }
}
