package com.aura.resolver.l2

import com.aura.domain.calc.NaturalMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4C — deterministic natural-language → infix-math normalization. No eval, no code.
 */
class NaturalMathTest {

    @Test
    fun word_operators_normalize() {
        assertEquals("500 + 200", NaturalMath.normalize("500 plus 200"))
        assertEquals("500 - 75", NaturalMath.normalize("500 minus 75"))
        assertEquals("25 * 4", NaturalMath.normalize("25 times 4"))
        assertEquals("100 / 5", NaturalMath.normalize("100 divided by 5"))
        assertEquals("25 * 4", NaturalMath.normalize("25 multiplied by 4"))
        assertEquals("100 / 5", NaturalMath.normalize("100 over 5"))
    }

    @Test
    fun percentage_of_normalizes() {
        assertEquals("(10/100)*500", NaturalMath.normalize("10% of 500"))
        assertEquals("(15/100)*4000", NaturalMath.normalize("15 percent of 4000"))
        assertEquals("(10.5/100)*200", NaturalMath.normalize("10.5% of 200"))
    }

    @Test
    fun non_math_input_is_not_forced() {
        assertNull(NaturalMath.normalize("hello world"))
        assertNull(NaturalMath.normalize("open chrome"))
        // arbitrary code must never be treated as math
        assertNull(NaturalMath.normalize("Runtime.getRuntime().exec('x')"))
        assertNull(NaturalMath.normalize("eval(1+1)"))
    }

    @Test
    fun lone_percentage_is_flagged_ambiguous() {
        assertTrue(NaturalMath.isLonePercentage("10 percent"))
        assertTrue(NaturalMath.isLonePercentage("10%"))
        assertTrue(NaturalMath.isLonePercentage("10% of")) // incomplete base
        assertFalse(NaturalMath.isLonePercentage("10% of 500"))
        assertFalse(NaturalMath.isLonePercentage("500 plus 200"))
    }
}
