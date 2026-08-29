package com.aura.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure arithmetic parser — precedence, parentheses, unary minus, modulo, and safety caps.
 * No eval, no functions, no arbitrary code.
 */
class ExpressionParserTest {

    private fun p(s: String) = ExpressionParser.parse(s)

    @Test
    fun integersAndPrecedence() {
        assertEquals(15.0, p("2 + 3 * 4 + 1"), 0.0)
        assertEquals(20.0, p("2 * (3 + 7)"), 0.0)
        assertEquals(-2.0, p("3 - 5"), 0.0)
    }

    @Test
    fun decimals() {
        assertEquals(6.0, p("2.5 + 3.5"), 0.0)
        assertEquals(2.5, p("5.0 / 2"), 0.0)
    }

    @Test
    fun parenthesesAndNesting() {
        assertEquals(90.0, p("(20 + 10) * 3"), 0.0)
        assertEquals(7.0, p("((1 + 2) * (3 - 1)) + 1"), 0.0)
    }

    @Test
    fun unaryNegativeAndLeadingMinus() {
        assertEquals(-4.0, p("-4"), 0.0)
        assertEquals(-12.0, p("-(3 + 9)"), 0.0)
        assertEquals(2.0, p("5 + -3"), 0.0)
    }

    @Test
    fun moduloOperator() {
        assertEquals(0.0, p("10 % 5"), 0.0)
        assertEquals(3.0, p("17 % 7"), 0.0)
        assertEquals(10.0, p("10 % 500"), 0.0)
    }

    @Test
    fun divisionByZeroIsRejected() {
        try {
            p("100 / 0")
            assertTrue("should throw", false)
        } catch (_: ArithmeticException) { /* expected */ }
        try {
            p("100 % 0")
            assertTrue("should throw", false)
        } catch (_: ArithmeticException) { /* expected */ }
    }

    @Test
    fun malformedInputIsRejected() {
        listOf("2 +", "* 3", "()", "3 +* 4", "abc", "2 3", "((1+2)").forEach { expr ->
            try {
                p(expr)
                assertTrue("expected failure for '$expr'", false)
            } catch (_: Exception) { /* expected */ }
        }
    }

    @Test
    fun tooLongExpressionIsRejected() {
        val big = "1+".repeat(500)
        try {
            p(big)
            assertTrue("should reject huge expression", false)
        } catch (_: Exception) { /* expected */ }
    }

    @Test
    fun whitespaceAndCaseSanity() {
        assertEquals(p("2+3*4"), p("  2 + 3 * 4  "), 0.0)
    }

    @Test
    fun largeValidExpressionWithinCapsEvaluates() {
        // 1+2+...+50 — well within token/length caps, resolves correctly (1275).
        val expr = (1..50).joinToString("+")
        assertEquals(1275.0, p(expr), 0.0)
    }
}
