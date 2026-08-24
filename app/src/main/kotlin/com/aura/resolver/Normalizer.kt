package com.aura.resolver

import java.text.Normalizer as JavaNormalizer
import java.util.Locale

/**
 * Deterministic normalization for L0 exact/prefix matching.
 * Handles: case folding, trim, repeated whitespace collapse, Unicode NFC.
 * Does NOT remove meaningful characters — goal is equality, not fuzzy.
 */
object Normalizer {

    /**
     * Normalize a raw query or indexed label.
     * Steps in order:
     * 1) Unicode NFC
     * 2) trim leading/trailing whitespace
     * 3) collapse internal repeated whitespace (including tabs) to single space
     * 4) case fold to lowercase (Locale.ROOT for determinism)
     */
    fun normalize(input: String): String {
        if (input.isEmpty()) return ""
        // 1) NFC
        var s = JavaNormalizer.normalize(input, JavaNormalizer.Form.NFC)
        // 2) trim
        s = s.trim()
        if (s.isEmpty()) return ""
        // 3) collapse whitespace: \s+ -> single space
        s = s.replace(Regex("\\s+"), " ")
        // 4) case fold
        s = s.lowercase(Locale.ROOT)
        return s
    }

    /**
     * Returns true if normalized query is empty (after normalization).
     * Caller should treat empty as Idle, not as Empty.
     */
    fun isEffectivelyEmpty(input: String): Boolean = normalize(input).isEmpty()
}
