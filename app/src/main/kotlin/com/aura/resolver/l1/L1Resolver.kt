package com.aura.resolver.l1

import com.aura.domain.CandidateGroup
import com.aura.domain.ResolvedResult
import com.aura.resolver.L0Index
import com.aura.resolver.Normalizer

/**
 * L1 Deterministic Grammar resolver — hand-maintained, no ML, no fuzzy.
 * Precedence documented (explicit, deterministic, auditable):
 *
 * 1. Percentage — 17% of 450000 (must be before Math, contains %)
 * 2. Unit — 10 km in miles (must be before Math, contains in/to)
 * 3. Math — 500 * 27, (500+27)*2
 * 4. Alarm — alarm 6:30, wake me at 6:30
 * 5. Timer — timer 10 min
 * 6. Call — call dad
 * 7. Message — message Sarah, tell Sarah ...
 * 8. Email — email Sarah, send email to Sarah
 * 9. Open — open Chrome
 * 10. Settings — wifi settings
 *
 * Each grammar receives normalized input and determines if it recognizes the input.
 * No giant regex — each grammar is isolated and testable.
 */
class L1Resolver(
    index: L0Index
) {
    private val grammars: List<L1Grammar> = listOf(
        PercentageGrammar(),
        UnitGrammar(),
        MathGrammar(),
        AlarmGrammar(),
        TimerGrammar(),
        CameraGrammar(),
        TimeQueryGrammar(),
        ScreenshotGrammar(),
        CallGrammar(index),
        MessageGrammar(index),
        EmailGrammar(index),
        OpenGrammar(index),
        SettingsGrammar(index)
    )

    fun resolve(rawQuery: String): L1Resolution {
        val normalized = Normalizer.normalize(rawQuery)
        if (normalized.isEmpty()) return L1Resolution.Idle
        for (grammar in grammars) {
            when (val result = grammar.parse(normalized, rawQuery)) {
                is L1Result.Resolved -> return L1Resolution.Resolved(result.result)
                is L1Result.Ambiguous -> return L1Resolution.Ambiguous(result.group)
                is L1Result.Invalid -> return L1Resolution.Invalid(result.message)
                is L1Result.Unrecognized -> continue // try next grammar
            }
        }
        return L1Resolution.Unrecognized(rawQuery)
    }
}

sealed interface L1Resolution {
    data object Idle : L1Resolution
    data class Resolved(val result: ResolvedResult) : L1Resolution
    data class Ambiguous(val group: CandidateGroup) : L1Resolution
    data class Invalid(val message: String) : L1Resolution
    data class Unrecognized(val query: String) : L1Resolution
}
