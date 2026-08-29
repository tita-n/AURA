package com.aura.domain.calc

/**
 * Pure arithmetic expression parser/evaluator. No eval(), no functions, no reflection,
 * no arbitrary code. Recursive-descent with operator precedence and unary minus.
 *
 * Supported: integers, decimals, parentheses, `+ - * / %`, unary negative.
 * Safety: bounded length / token count / parenthesis depth; division and modulo by zero
 * are rejected; NaN/Infinity are rejected.
 */
object ExpressionParser {
    private const val MAX_LENGTH = 200
    private const val MAX_TOKENS = 400
    private const val MAX_DEPTH = 50

    fun parse(input: String): Double {
        val src = input.trim()
        if (src.length > MAX_LENGTH) throw IllegalArgumentException("Expression too long")
        val tokens = tokenize(src)
        if (tokens.isEmpty()) throw IllegalArgumentException("Empty expression")
        val parser = Parser(tokens)
        val result = parser.parseExpr()
        if (parser.pos != tokens.size) throw IllegalArgumentException("Unexpected token")
        if (result.isNaN() || result.isInfinite()) throw IllegalArgumentException("Invalid result")
        return result
    }

    private sealed interface Tok {
        data class Num(val value: Double) : Tok
        data class Op(val c: Char) : Tok
        data object LP : Tok
        data object RP : Tok
    }

    private fun tokenize(input: String): List<Tok> {
        val out = mutableListOf<Tok>()
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
                    val v = input.substring(i, j).toDoubleOrNull()
                        ?: throw IllegalArgumentException("Invalid number")
                    out.add(Tok.Num(v))
                    i = j
                }
                c == '(' -> { out.add(Tok.LP); i++ }
                c == ')' -> { out.add(Tok.RP); i++ }
                c in "+-*/%" -> { out.add(Tok.Op(c)); i++ }
                else -> throw IllegalArgumentException("Invalid character: '$c'")
            }
        }
        if (out.size > MAX_TOKENS) throw IllegalArgumentException("Expression too large")
        return out
    }

    private class Parser(private val tokens: List<Tok>) {
        var pos = 0

        fun parseExpr(): Double {
            var value = parseTerm()
            while (pos < tokens.size) {
                val op = tokens[pos] as? Tok.Op ?: break
                if (op.c != '+' && op.c != '-') break
                pos++
                value += if (op.c == '+') parseTerm() else -parseTerm()
            }
            return value
        }

        fun parseTerm(): Double {
            var value = parseFactor()
            while (pos < tokens.size) {
                val op = tokens[pos] as? Tok.Op ?: break
                if (op.c != '*' && op.c != '/' && op.c != '%') break
                pos++
                val rhs = parseFactor()
                value = when (op.c) {
                    '*' -> value * rhs
                    '/' -> {
                        if (rhs == 0.0) throw ArithmeticException("Division by zero")
                        value / rhs
                    }
                    '%' -> {
                        if (rhs == 0.0) throw ArithmeticException("Modulo by zero")
                        value % rhs
                    }
                    else -> value
                }
            }
            return value
        }

        fun parseFactor(): Double {
            if (pos >= tokens.size) throw IllegalArgumentException("Unexpected end of expression")
            return when (val t = tokens[pos]) {
                is Tok.Num -> { pos++; t.value }
                is Tok.LP -> {
                    pos++
                    val depth = depthCheck()
                    val v = parseExpr()
                    if (pos >= tokens.size || tokens[pos] !is Tok.RP) {
                        throw IllegalArgumentException("Missing closing parenthesis")
                    }
                    pos++
                    v
                }
                is Tok.Op -> {
                    if (t.c == '-') { pos++; -parseFactor() }
                    else if (t.c == '+') { pos++; parseFactor() }
                    else throw IllegalArgumentException("Unexpected operator '${t.c}'")
                }
                is Tok.RP -> throw IllegalArgumentException("Unexpected ')'")
            }
        }

        private fun modulo(a: Double, b: Double): Double {
            if (b == 0.0) throw ArithmeticException("Modulo by zero")
            return a % b
        }

        private fun depthCheck(): Int {
            // Count open parens currently on the stack is overkill; rely on a global budget.
            var open = 0
            for (k in 0 until pos) if (tokens[k] is Tok.LP) open++
            if (open > MAX_DEPTH) throw IllegalArgumentException("Expression too deeply nested")
            return open
        }
    }
}
