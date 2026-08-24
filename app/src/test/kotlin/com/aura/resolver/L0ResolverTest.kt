package com.aura.resolver

import com.aura.domain.*
import org.junit.Assert.*
import org.junit.Test

class L0ResolverTest {

    private fun app(label: String, pkg: String = "com.test.${label.lowercase().replace(" ", "")}") =
        L0IndexFactory.appEntity(pkg, label)
    private fun contact(name: String, id: String, dis: String = "test") =
        L0IndexFactory.contactEntity(id, name, dis)
    private fun settings(label: String, key: String) =
        L0IndexFactory.settingsEntity(key, label)

    private fun routerWith(vararg entities: IndexedEntity): IntentRouter {
        val index = L0Index.build(entities.toList())
        return IntentRouter(L0Resolver(index))
    }

    @Test
    fun `exact app match to Act`() {
        val router = routerWith(app("Chrome"), app("WhatsApp"))
        val out = router.route("chrome")
        assertTrue(out is ResolutionOutcome.Act)
        assertEquals("Chrome", (out as ResolutionOutcome.Act).result.title)
    }

    @Test
    fun `exact contact match to Act only if single`() {
        val router = routerWith(contact("Sarah", "1", "a"))
        val out = router.route("sarah")
        assertTrue(out is ResolutionOutcome.Act)
    }

    @Test
    fun `exact settings match to Act`() {
        val router = routerWith(settings("Wi-Fi", "wifi"))
        val out = router.route("wi-fi")
        assertTrue(out is ResolutionOutcome.Act)
        assertEquals("Wi-Fi", (out as ResolutionOutcome.Act).result.title)
    }

    @Test
    fun `case-insensitive`() {
        val router = routerWith(app("Chrome"))
        assertTrue(router.route("CHROME") is ResolutionOutcome.Act)
        assertTrue(router.route("ChRoMe") is ResolutionOutcome.Act)
    }

    @Test
    fun `whitespace normalization`() {
        val router = routerWith(app("Chrome"))
        assertTrue(router.route("  chrome  ") is ResolutionOutcome.Act)
        assertTrue(router.route("  CHROME  ") is ResolutionOutcome.Act)
    }

    @Test
    fun `exact beats prefix`() {
        val router = routerWith(app("Chrome"), app("Chrome Beta"))
        val out = router.route("chrome")
        assertTrue(out is ResolutionOutcome.Act)
        assertEquals("Chrome", (out as ResolutionOutcome.Act).result.title)
    }

    @Test
    fun `multiple exact matches to Ask`() {
        val router = routerWith(
            app("TestApp", "com.a.one"),
            app("TestApp", "com.a.two")
        )
        val out = router.route("testapp")
        assertTrue(out is ResolutionOutcome.Ask)
        val group = (out as ResolutionOutcome.Ask).group
        assertEquals(2, group.candidates.size)
        assertTrue(group.label.contains("Which", ignoreCase = true))
    }

    @Test
    fun `multiple contacts same name to Ask with disambiguation`() {
        val router = routerWith(
            contact("Sarah", "1", "sarah.one@email.com"),
            contact("Sarah", "2", "called yesterday")
        )
        val out = router.route("sarah")
        assertTrue(out is ResolutionOutcome.Ask)
        val group = (out as ResolutionOutcome.Ask).group
        assertEquals(2, group.candidates.size)
        group.candidates.forEach { c ->
            assertNotNull(c.disambiguation)
            assertTrue(c.disambiguation!!.isNotBlank())
        }
        assertEquals("Which Sarah", group.label)
    }

    @Test
    fun `candidate group has no preselection`() {
        val router = routerWith(
            contact("Sarah", "1", "a"),
            contact("Sarah", "2", "b")
        )
        val out = router.route("sarah") as ResolutionOutcome.Ask
        assertEquals(2, out.group.candidates.size)
    }

    @Test
    fun `no match is not Error`() {
        val router = routerWith(app("Chrome"))
        val out = router.route("unknownxyz")
        assertTrue(out is ResolutionOutcome.Empty)
        assertFalse(out is ResolutionOutcome.Error)
    }

    @Test
    fun `no L0 confidence reaches UI`() {
        val router = routerWith(app("Chrome"), contact("Sarah", "1", "a"), contact("Sarah", "2", "b"))
        val act = router.route("chrome") as ResolutionOutcome.Act
        val ask = router.route("sarah") as ResolutionOutcome.Ask
        listOf(act, ask).forEach { o ->
            val str = o.toString().lowercase()
            assertFalse(str.contains("confidence"))
            assertFalse(str.contains("l0"))
            assertFalse(str.contains("layer"))
        }
        assertFalse(act.result.toString().lowercase().contains("confidence"))
    }

    @Test
    fun `L0 result can become Act without exposing L0 identity`() {
        val router = routerWith(app("Chrome"))
        val out = router.route("chrome")
        assertTrue(out is ResolutionOutcome.Act)
        val cmd = out.toCommandState()
        assertTrue(cmd is CommandState.Act)
        assertFalse(cmd.toString().contains("L0"))
    }

    @Test
    fun `L0 ambiguity becomes Ask`() {
        val router = routerWith(
            app("TestApp", "com.a.one"),
            app("TestApp", "com.a.two")
        )
        val out = router.route("testapp")
        assertTrue(out is ResolutionOutcome.Ask)
        assertTrue(out.toCommandState() is CommandState.Ask)
    }

    @Test
    fun `empty query remains Idle`() {
        val router = routerWith(app("Chrome"))
        assertTrue(router.route("") is ResolutionOutcome.Idle)
        assertTrue(router.route("   ") is ResolutionOutcome.Idle)
        assertTrue(router.route("").toCommandState() is CommandState.Idle)
    }

    @Test
    fun `prefix single to Act`() {
        val router = routerWith(app("Chrome"), app("WhatsApp"))
        val out = router.route("chr")
        assertTrue(out is ResolutionOutcome.Act)
        assertEquals("Chrome", (out as ResolutionOutcome.Act).result.title)
    }

    @Test
    fun `prefix multiple to Ask Did you mean`() {
        val router = routerWith(app("Chrome"), app("Chrome Beta"), app("Calculator"))
        val out = router.route("ch")
        assertTrue(out is ResolutionOutcome.Ask)
        assertEquals("Did you mean", (out as ResolutionOutcome.Ask).group.label)
    }

    @Test
    fun `repeated queries use existing index`() {
        val index = L0Index.build(listOf(app("Chrome"), app("WhatsApp")))
        val resolver = L0Resolver(index)
        val r1 = resolver.resolve("chrome")
        val r2 = resolver.resolve("chrome")
        assertTrue(r1 is L0Resolution.Resolved)
        assertTrue(r2 is L0Resolution.Resolved)
        assertEquals((r1 as L0Resolution.Resolved).result.id, (r2 as L0Resolution.Resolved).result.id)
        assertEquals(2, index.size())
    }

    @Test
    fun `contact prefix vs exact distinction`() {
        val router = routerWith(
            contact("Sarah", "1", "a"),
            contact("Sarah M.", "2", "b")
        )
        val exact = router.route("sarah")
        assertTrue(exact is ResolutionOutcome.Act)
        val prefix = router.route("sarah m")
        assertTrue(prefix is ResolutionOutcome.Act)
        assertEquals("Sarah M.", (prefix as ResolutionOutcome.Act).result.title)
    }
}
