package com.aura.resolver.l2

import com.aura.resolver.L0Index
import com.aura.resolver.Normalizer

/**
 * L2 Semantic Resolution — deterministic, no ML, no network.
 * Handles typo tolerance, synonym tables, and semantic variants for existing AURA capabilities.
 * Only runs after L0 and L1 unresolved. Never overrides deterministic results.
 *
 * Priority (explicit, deterministic):
 * 1. Open app (typo + synonyms)
 * 2. Call
 * 3. Message
 * 4. Email
 * 5. Settings
 * 6. Timer (variant phrasings)
 * 7. Math (what is ...)
 * 8. Unit (convert ... to ...)
 */
class L2Resolver(
    private val index: L0Index
) {
    private val openMatcher = OpenAppMatcher(index)
    private val callMatcher = CallMatcher(index)
    private val messageMatcher = MessageMatcher(index)
    private val emailMatcher = EmailMatcher(index)
    private val settingsMatcher = SettingsMatcher(index)
    private val timerMatcher = TimerMatcher()
    private val mathMatcher = MathMatcher()
    private val unitMatcher = UnitMatcher()

    // Order matters: explicit priority
    private val orderedMatchers: List<Pair<String, (String, String) -> L2Result>> = listOf(
        "Open" to { n, r -> openMatcher.match(n, r) },
        "Call" to { n, r -> callMatcher.match(n, r) },
        "Message" to { n, r -> messageMatcher.match(n, r) },
        "Email" to { n, r -> emailMatcher.match(n, r) },
        "Settings" to { n, r -> settingsMatcher.match(n, r) },
        "Timer" to { n, r -> timerMatcher.match(n, r) },
        "Math" to { n, r -> mathMatcher.match(n, r) },
        "Unit" to { n, r -> unitMatcher.match(n, r) }
    )

    fun resolve(rawQuery: String): L2Result {
        val normalized = Normalizer.normalize(rawQuery)
        if (normalized.isEmpty()) return L2Result.Unrecognized

        for ((_, matcher) in orderedMatchers) {
            when (val result = matcher(normalized, rawQuery)) {
                is L2Result.Resolved -> return result
                is L2Result.Ambiguous -> return result
                is L2Result.Invalid -> return result
                is L2Result.Unrecognized -> continue
            }
        }
        return L2Result.Unrecognized
    }
}
