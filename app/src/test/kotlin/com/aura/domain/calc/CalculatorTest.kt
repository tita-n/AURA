package com.aura.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Natural-language calculator through the real pipeline: English → infix → controlled evaluator.
 * Covers the documented examples plus safety (malformed, divide-by-zero, ambiguous percentages).
 */
class CalculatorTest {

    private fun calc(s: String): String {
        val expr = Calculator.preprocess(s) ?: return "UNRECOGNIZED"
        return when (val r = Calculator.evaluate(expr)) {
            is CalculatorResult.Ok -> Calculator.format(r.value)
            is CalculatorResult.Invalid -> "INVALID"
        }
    }

    @Test
    fun documentedExamples() {
        assertEquals("50", calc("10% of 500"))
        assertEquals("50", calc("what is 10% of 500"))
        assertEquals("600", calc("calculate 15% of 4000"))
        assertEquals("1200", calc("what's 15 percent of 8000"))
        assertEquals("300", calc("how much is 20% of 1500"))
        assertEquals("600", calc("4000 * 0.15"))
        assertEquals("500", calc("4000 / 8"))
        assertEquals("42", calc("25 + 17"))
        assertEquals("65", calc("100 - 35"))
        assertEquals("168", calc("12 * 14"))
        assertEquals("12", calc("144 / 12"))
        assertEquals("20", calc("2.5 * 8"))
        assertEquals("90", calc("(20 + 10) * 3"))
    }

    @Test
    fun wordOperators() {
        assertEquals("700", calc("500 plus 200"))
        assertEquals("425", calc("500 minus 75"))
        assertEquals("100", calc("25 times 4"))
        assertEquals("20", calc("100 divided by 5"))
    }

    @Test
    fun percentageIncreaseAndDecrease() {
        assertEquals("550", calc("500 increased by 10%"))
        assertEquals("400", calc("500 reduced by 20%"))
        assertEquals("750", calc("500 up by 50%"))
    }

    @Test
    fun percentOff() {
        assertEquals("42.5", calc("15% off 50"))
        assertEquals("42.5", calc("15% off 50.0"))
    }

    @Test
    fun percentageSemanticsAreExplicit() {
        // "10% + 20%" is the sum of two fractions (0.30), NOT 10% of 20.
        assertEquals("0.3", calc("10% + 20%"))
        // "10% of 20" is a clear base — 2.
        assertEquals("2", calc("10% of 20"))
        assertFalse(calc("10% + 20%") == calc("10% of 20"))
    }

    @Test
    fun invalidAndAmbiguous() {
        assertEquals("INVALID", calc("100 divided by 0"))      // division by zero
        assertEquals("UNRECOGNIZED", calc("10 percent"))      // lone percentage — ambiguous, not guessed
        assertEquals("UNRECOGNIZED", calc("open chrome"))      // not math
    }

    @Test
    fun noArbitraryCodeExecution() {
        listOf(
            "Runtime.getRuntime().exec('x')",
            "eval(1+1)",
            "system('ls')",
            "import os"
        ).forEach { s ->
            assertEquals("must not evaluate code: '$s'", "UNRECOGNIZED", calc(s))
        }
    }

    @Test
    fun formattingIsNative() {
        assertEquals("50", Calculator.format(50.0))
        assertEquals("50", Calculator.format(50.0))
        assertEquals("1200", Calculator.format(1200.0))
        assertEquals("0.1", Calculator.format(0.1))
        assertEquals("2.5", Calculator.format(2.5))
    }
}
