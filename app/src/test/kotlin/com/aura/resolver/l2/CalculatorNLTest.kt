package com.aura.resolver.l2

import com.aura.resolver.Normalizer
import com.aura.resolver.l1.L1Result
import com.aura.resolver.l1.PercentageGrammar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4C — natural-language calculator through the real pipeline.
 * Verifies the documented examples resolve, invalid/ambiguous/divide-by-zero are handled
 * honestly, and no arbitrary code is ever executed.
 */
class CalculatorNLTest {

    private fun match(q: String) = MathMatcher().match(Normalizer.normalize(q), q)
    private fun title(q: String): String {
        val r = match(q)
        assertTrue("expected Resolved for '$q'", r is L2Result.Resolved)
        return (r as L2Result.Resolved).result.title
    }

    @Test
    fun percentage_strict_form() {
        assertEquals("50", PercentageGrammar().parse("10% of 500", "10% of 500").let {
            assertTrue(it is com.aura.resolver.l1.L1Result.Resolved)
            (it as com.aura.resolver.l1.L1Result.Resolved).result.title
        })
        assertEquals("50", title("10% of 500"))
    }

    @Test
    fun percentage_with_words() {
        assertEquals("50", title("what is 10% of 500"))
        assertEquals("600", title("calculate 15% of 4000"))
        assertEquals("170", title("what is 20 percent of 850"))
        assertEquals("450", title("how much is 15% of 3000"))
        assertEquals("21", title("10.5% of 200"))
    }

    @Test
    fun word_operators() {
        assertEquals("700", title("500 plus 200"))
        assertEquals("425", title("500 minus 75"))
        assertEquals("100", title("25 times 4"))
        assertEquals("20", title("100 divided by 5"))
        assertEquals("6", title("2.5 plus 3.5"))
    }

    @Test
    fun decimals() {
        assertEquals("21", title("10.5% of 200"))
        assertEquals("6", title("2.5 plus 3.5"))
    }

    @Test
    fun invalid_expression_is_invalid_not_faked() {
        val r = match("500 plus")
        assertTrue(r is L2Result.Invalid)
    }

    @Test
    fun ambiguous_lone_percentage_is_invalid_not_guessed() {
        val r = match("10 percent")
        assertTrue(r is L2Result.Invalid)
    }

    @Test
    fun division_by_zero_is_invalid_not_crash() {
        val r = match("100 divided by 0")
        assertTrue(r is L2Result.Invalid)
    }

    @Test
    fun no_arbitrary_code_execution() {
        // A code-looking string must never become a Resolved math result.
        assertFalse(match("Runtime.getRuntime().exec('rm -rf')") is L2Result.Resolved)
        assertFalse(match("eval(1+1)") is L2Result.Resolved)
        assertFalse(match("system('ls')") is L2Result.Resolved)
    }
}
