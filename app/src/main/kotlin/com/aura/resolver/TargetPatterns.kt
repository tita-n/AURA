package com.aura.resolver

/**
 * Deterministic shape validators for direct (contactless) communication targets.
 * Pure Kotlin — no Android APIs. Used by L1 grammars and L3 validation so both
 * agree on what constitutes a well-formed raw target.
 */
object TargetPatterns {

    // Digits with optional leading +, separators (space dash paren dot)
    private val phoneRegex = Regex("""^\+?[0-9][0-9\s\-().]{2,20}$""")
    private val emailRegex = Regex("""^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$""")

    fun isPhoneLike(raw: String): Boolean {
        val s = raw.trim()
        if (s.isEmpty()) return false
        // Must contain at least one digit and only phone-permitted characters
        if (!s.any { it.isDigit() }) return false
        return phoneRegex.matches(s)
    }

    fun isEmailLike(raw: String): Boolean = emailRegex.matches(raw.trim())
}
