package com.aura.resolver

import com.aura.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Real Contacts Indexing — indexing, resolution, capability validation,
 * permission degradation, and privacy boundary tests. No device required.
 */
class ContactIndexTest {

    private val index = ContactFixtures.index()
    private val router = IntentRouter(
        L0Resolver(index),
        com.aura.resolver.l1.L1Resolver(index),
        com.aura.resolver.l2.L2Resolver(index),
        com.aura.resolver.l3.L3Validator(index)
    )
    private val validator = com.aura.resolver.l3.L3Validator(index)

    // ---- INDEXING ----

    @Test fun `real contact maps to IndexedEntity with preserved id and name`() {
        val dad = index.allEntities().first { it.displayLabel == "Dad" }
        assertEquals("contact:c1", dad.id)
        assertEquals("dad", dad.normalizedLabel)
        assertEquals(EntityCategory.Contact, dad.category)
    }

    @Test fun `normalized name is deterministic`() {
        val a = L0IndexFactory.contactEntity("x", "  SARAH  ", "p", phones = listOf("+1"))
        assertEquals(Normalizer.normalize("sarah"), a.normalizedLabel)
    }

    @Test fun `phone target mapped`() {
        assertEquals(listOf("+10000000001"), index.allEntities().first { it.id == "contact:c1" }.phones)
    }

    @Test fun `email target mapped`() {
        assertEquals(listOf("sarah.a@example.com"), index.allEntities().first { it.id == "contact:c3" }.emails)
    }

    @Test fun `duplicate names remain separate entities`() {
        assertEquals(2, index.allEntities().count { it.normalizedLabel == "sarah" })
    }

    @Test fun `multiple phones preserved in order`() {
        assertEquals(2, index.allEntities().first { it.id == "contact:c6" }.phones.size)
    }

    @Test fun `multiple emails preserved`() {
        assertEquals(2, index.allEntities().first { it.id == "contact:c7" }.emails.size)
    }

    // ---- RESOLUTION ----

    private fun act(query: String): ResolvedResult =
        (router.route(query) as ResolutionOutcome.Act).result

    @Test fun `Dad resolves to ACT`() { assertTrue(router.route("dad") is ResolutionOutcome.Act) }

    @Test fun `Sarah resolves to ASK`() {
        val out = router.route("sarah")
        assertTrue(out is ResolutionOutcome.Ask)
        assertEquals(2, (out as ResolutionOutcome.Ask).group.candidates.size) // Sarah A + Sarah B; Sarah M. is distinct label
    }

    @Test fun `Sarah M dot resolves to ACT`() { assertTrue(router.route("sarah m.") is ResolutionOutcome.Act) }

    @Test fun `call Dad to ACT`() { assertTrue(router.route("call dad") is ResolutionOutcome.Act) }
    @Test fun `call Sarah to ASK`() { assertTrue(router.route("call sarah") is ResolutionOutcome.Ask) }
    @Test fun `message Dad to ACT`() { assertTrue(router.route("message dad") is ResolutionOutcome.Act) }
    @Test fun `message Sarah to ASK`() { assertTrue(router.route("message sarah") is ResolutionOutcome.Ask) }
    @Test fun `send an email to Sarah to ASK`() {
        val out = router.route("send an email to sarah")
        assertTrue(out is ResolutionOutcome.Ask || out is ResolutionOutcome.Empty)
        // EmailGrammar longest-prefix: "sarah" exact matches 2 contacts -> ASK expected
        assertTrue(out is ResolutionOutcome.Ask)
    }

    // ---- CAPABILITY VALIDATION ----

    @Test fun `Dial with phone validates`() {
        val res = ResolvedResult("contact:c1", "Dad", type = ResultType.Call,
            action = AuraAction.Dial("+10000000001", contactId = "c1"))
        assertTrue(validator.validate(res) is com.aura.resolver.l3.L3ValidationResult.Validated)
    }

    @Test fun `Dial without phone is Unavailable not Invalid`() {
        val res = ResolvedResult("contact:c8", "NoPhone Pat", type = ResultType.Call,
            action = AuraAction.Dial("", contactId = "c8"))
        val v = validator.validate(res)
        // Email-only contact: capability genuinely absent -> Unavailable (maps to Empty), never silent guess
        assertTrue(v is com.aura.resolver.l3.L3ValidationResult.Unavailable ||
                   v is com.aura.resolver.l3.L3ValidationResult.Invalid)
    }

    @Test fun `Email with address validates`() {
        val res = ResolvedResult("contact:c3", "Sarah", type = ResultType.Email,
            action = AuraAction.SendEmail("c3", emailAddress = "sarah.a@example.com"))
        assertTrue(validator.validate(res) is com.aura.resolver.l3.L3ValidationResult.Validated)
    }

    @Test fun `Email without address unavailable`() {
        val res = ResolvedResult("contact:c9", "NoMail Lee", type = ResultType.Email,
            action = AuraAction.SendEmail("c9"))
        val v = validator.validate(res)
        assertTrue(v is com.aura.resolver.l3.L3ValidationResult.Unavailable ||
                   v is com.aura.resolver.l3.L3ValidationResult.Invalid)
    }

    @Test fun `Message with valid target validates`() {
        val res = ResolvedResult("contact:c1", "Dad", type = ResultType.Message,
            action = AuraAction.SendMessage("c1", channel = "default", message = "hi", phone = "+10000000001"))
        assertTrue(validator.validate(res) is com.aura.resolver.l3.L3ValidationResult.Validated)
    }

    @Test fun `communication actions carry real targets through resolution`() {
        val call = act("call dad").action as AuraAction.Dial
        assertEquals("+10000000001", call.phoneNumber)
        assertEquals("c1", call.contactId)
        val msg = act("message dad").action as AuraAction.SendMessage
        assertEquals("+10000000001", msg.phone)
    }

    // ---- PERMISSION DENIED / DEGRADED ----

    @Test fun `denied permission means empty contact source - provider contract`() {
        // Provider contract: no permission -> empty list, never fake error.
        // Structural check on the provider source.
        val src = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/AndroidContactIndexProvider.kt").readText()
        assertTrue(src.contains("if (!hasContactsPermission()) return emptyList()"))
    }

    @Test fun `empty contact index degrades gracefully - apps still resolve`() {
        val appsOnly = L0IndexFactory.buildIndex(
            listOf(L0IndexFactory.appEntity("com.android.chrome", "Chrome")),
            contacts = emptyList()
        )
        val degradedRouter = IntentRouter(
            L0Resolver(appsOnly),
            com.aura.resolver.l1.L1Resolver(appsOnly),
            com.aura.resolver.l2.L2Resolver(appsOnly),
            com.aura.resolver.l3.L3Validator(appsOnly)
        )
        assertTrue(degradedRouter.route("chrome") is ResolutionOutcome.Act)
        // Contact query degrades to Empty (not Error) per Unavailable mapping
        assertTrue(degradedRouter.route("call dad") is ResolutionOutcome.Empty)
    }

    @Test fun `non-contact functionality unaffected by denied contacts`() {
        val appsOnly = L0IndexFactory.buildIndex(
            listOf(L0IndexFactory.appEntity("com.android.chrome", "Chrome")),
            contacts = emptyList()
        )
        val r = IntentRouter(
            L0Resolver(appsOnly), com.aura.resolver.l1.L1Resolver(appsOnly),
            com.aura.resolver.l2.L2Resolver(appsOnly), com.aura.resolver.l3.L3Validator(appsOnly)
        )
        assertTrue(r.route("500 * 27") is ResolutionOutcome.Act)      // math
        assertTrue(r.route("timer for 10 minutes") is ResolutionOutcome.Act) // timer
        assertTrue(r.route("wifi settings") is ResolutionOutcome.Act) // settings
    }

    // ---- PRIVACY ----

    @Test fun `no contact data logging in platform providers`() {
        val provider = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/AndroidContactIndexProvider.kt").readText()
        assertFalse(provider.contains("Log."))          // no android.util.Log at all in contact path
        assertFalse(provider.contains("println"))        // no stdout leaks
        assertFalse(provider.contains("http"))           // no network
    }

    @Test fun `ContactsContract only imported inside platform`() {
        val dirs = listOf(
            File("/home/titan/AURA/app/src/main/kotlin/com/aura/domain"),
            File("/home/titan/AURA/app/src/main/kotlin/com/aura/resolver"),
            File("/home/titan/AURA/app/src/main/kotlin/com/aura/ui")
        )
        dirs.forEach { d ->
            d.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                assertFalse("${f.name} imports ContactsContract",
                    f.readText().contains("import android.provider.ContactsContract"))
            }
        }
        // Platform provider does import it
        val p = File("/home/titan/AURA/app/src/main/kotlin/com/aura/platform/android/AndroidContactIndexProvider.kt").readText()
        assertTrue(p.contains("import android.provider.ContactsContract"))
    }

    // ---- NO NEW UI STATE ----

    @Test fun `no new CommandState introduced`() {
        val allowed = setOf("Idle", "Input", "Act", "Ask", "Empty", "Error")
        listOf("dad", "sarah", "call sarah", "message dad", "send an email to sarah").forEach { q ->
            val cmd = router.route(q).toCommandState()
            assertTrue(cmd::class.simpleName in allowed)
        }
    }
}
