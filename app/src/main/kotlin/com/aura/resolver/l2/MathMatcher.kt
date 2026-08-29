package com.aura.resolver.l2

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType
import com.aura.domain.calc.Calculator
import com.aura.domain.calc.CalculatorResult
import com.aura.domain.calc.NaturalMath

/**
 * Math — natural-language arithmetic. Resolves immediately (pure local computation).
 *
 * Strips calculator question phrasing and English operators/percentages into a strict infix
 * expression and evaluates it with the controlled [Calculator] parser — no eval, no functions,
 * no arbitrary code. A lone percentage ("10 percent", no base) is treated as ambiguous and
 * surfaced as Invalid rather than guessed.
 */
class MathMatcher {
    fun match(normalized: String, raw: String): L2Result {
        val expr = raw.trim()
        if (expr.isEmpty()) return L2Result.Unrecognized
        // Ambiguous lone percentage — never guess a number without a base.
        if (NaturalMath.isLonePercentage(expr)) return L2Result.Invalid("Ambiguous percentage")
        val mathExpr = Calculator.preprocess(expr) ?: return L2Result.Unrecognized
        return when (val result = Calculator.evaluate(mathExpr)) {
            is CalculatorResult.Ok -> L2Result.Resolved(
                ResolvedResult(
                    id = "math:${expr.lowercase()}",
                    title = Calculator.format(result.value),
                    subtitle = null,
                    type = ResultType.Math,
                    action = AuraAction.Copy(Calculator.format(result.value)),
                    inlineValue = Calculator.format(result.value),
                    inlineQuery = expr
                )
            )
            is CalculatorResult.Invalid -> L2Result.Invalid("Invalid expression")
        }
    }
}
