package com.aura.resolver.l1

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType

/**
 * Math — deterministic arithmetic: 500 * 27, (500 + 27) * 2, etc.
 * Supports + - * / and parentheses, decimal numbers, whitespace variation.
 * No eval(), no functions, no arbitrary code.
 */
class MathGrammar : L1Grammar {
    override fun name() = "Math"

    // Strict math pattern: only digits, dot, +-*/(), whitespace
    private val mathChars = Regex("""^[\d\s\.\+\-\*\/\(\)]+$""")

    override fun parse(normalized: String, raw: String): L1Result {
        // Quick reject: must contain at least one digit and one operator or parentheses
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return L1Result.Unrecognized
        // Use raw trimmed for character check (preserve original spacing for display)
        if (!mathChars.matches(trimmed)) return L1Result.Unrecognized
        if (!trimmed.any { it.isDigit() }) return L1Result.Unrecognized
        // Must contain operator or parentheses to be considered math, not just a number
        if (!trimmed.any { it in "+-*/()" }) return L1Result.Unrecognized
        // Also ensure it doesn't contain letters — already via mathChars
        return try {
            val result = MathParser.evaluate(trimmed)
            // Handle division by zero already throws
            val formatted = formatResult(result)
            L1Result.Resolved(
                ResolvedResult(
                    id = "math:${trimmed}",
                    title = formatted,
                    subtitle = null,
                    type = ResultType.Math,
                    action = AuraAction.Copy(formatted),
                    inlineValue = formatted,
                    inlineQuery = raw.trim()
                )
            )
        } catch (e: ArithmeticException) {
            L1Result.Invalid(e.message ?: "Invalid arithmetic")
        } catch (e: IllegalArgumentException) {
            // Malformed expression -> Invalid (recognized but invalid)
            L1Result.Invalid(e.message ?: "Malformed expression")
        }
    }

    private fun formatResult(value: Double): String {
        // Remove trailing .0 for integers, otherwise keep as is
        return if (value % 1.0 == 0.0) {
            // Avoid scientific notation for large ints
            val longVal = value.toLong()
            longVal.toString()
        } else {
            // Trim trailing zeros
            var s = value.toString()
            if (s.contains("E")) return s // scientific, leave
            // Remove trailing zeros after decimal
            s = s.trimEnd('0').trimEnd('.')
            s
        }
    }
}

/**
 * Constrained tokenizer/parser — no eval.
 * Grammar:
 *   expr   = term (('+'|'-') term)*
 *   term   = factor (('*'|'/' ) factor)*
 *   factor = number | '(' expr ')' | '-' factor
 */
internal object MathParser {
    fun evaluate(input: String): Double {
        val tokens = tokenize(input)
        if (tokens.isEmpty()) throw IllegalArgumentException("Empty expression")
        val parser = Parser(tokens)
        val result = parser.parseExpr()
        if (parser.pos != tokens.size) throw IllegalArgumentException("Unexpected token")
        if (result.isNaN() || result.isInfinite()) throw ArithmeticException("Invalid result")
        return result
    }

    private sealed interface Token {
        data class Number(val value: Double) : Token
        data class Op(val c: Char) : Token
        data object LParen : Token
        data object RParen : Token
    }

    private fun tokenize(input: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    var j = i
                    var dots = 0
                    while (j < input.length && (input[j].isDigit() || input[j] == '.')) {
                        if (input[j] == '.') dots++
                        j++
                    }
                    if (dots > 1) throw IllegalArgumentException("Invalid number")
                    val numStr = input.substring(i, j)
                    val v = numStr.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number: $numStr")
                    out.add(Token.Number(v))
                    i = j
                }
                c == '(' -> { out.add(Token.LParen); i++ }
                c == ')' -> { out.add(Token.RParen); i++ }
                c in "+-*/" -> { out.add(Token.Op(c)); i++ }
                else -> throw IllegalArgumentException("Invalid character: $c")
            }
        }
        return out
    }

    private class Parser(private val tokens: List<Token>) {
        var pos = 0
        fun parseExpr(): Double {
            var value = parseTerm()
            while (pos < tokens.size) {
                val op = tokens[pos] as? Token.Op ?: break
                if (op.c != '+' && op.c != '-') break
                pos++
                val rhs = parseTerm()
                value = if (op.c == '+') value + rhs else value - rhs
            }
            return value
        }
        fun parseTerm(): Double {
            var value = parseFactor()
            while (pos < tokens.size) {
                val op = tokens[pos] as? Token.Op ?: break
                if (op.c != '*' && op.c != '/') break
                pos++
                val rhs = parseFactor()
                value = if (op.c == '*') value * rhs else {
                    if (rhs == 0.0) throw ArithmeticException("Division by zero")
                    value / rhs
                }
            }
            return value
        }
        fun parseFactor(): Double {
            if (pos >= tokens.size) throw IllegalArgumentException("Unexpected end")
            return when (val t = tokens[pos]) {
                is Token.Number -> { pos++; t.value }
                is Token.LParen -> {
                    pos++
                    val v = parseExpr()
                    if (pos >= tokens.size || tokens[pos] !is Token.RParen) throw IllegalArgumentException("Missing )")
                    pos++
                    v
                }
                is Token.Op -> {
                    if (t.c == '-') {
                        pos++
                        -parseFactor()
                    } else throw IllegalArgumentException("Unexpected operator ${t.c}")
                }
                is Token.RParen -> throw IllegalArgumentException("Unexpected )")
            }
        }
    }
}
