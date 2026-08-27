package com.aura.resolver.l1

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType

/**
 * Screenshot — a recognized but UNSUPPORTED capability.
 *
 * Android does not permit ordinary third-party apps (including launchers) to capture the
 * screen without elevated privileges: a device-owner/root API, a MediaProjection flow that
 * shows a system "screen capture" consent dialog, or an AccessibilityService. AURA uses none
 * of these — they would be privacy-invasive and are explicitly out of scope.
 *
 * Therefore AURA must NOT claim "Screenshot taken". It honestly reports the platform
 * limitation and tells the user the hardware shortcut. No fake success, no fabricated action.
 */
class ScreenshotGrammar : L1Grammar {
    override fun name() = "Screenshot"

    private val patterns = listOf(
        Regex("""^\s*screenshot\s*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*(?:take|capture|grab|snap)\s+(?:a\s+)?screenshot\s*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*(?:screen\s+capture|capture\s+screen|screen\s+shot)\s*$""", RegexOption.IGNORE_CASE)
    )

    override fun parse(normalized: String, raw: String): L1Result {
        val trimmed = raw.trim()
        val match = patterns.firstNotNullOfOrNull { it.matchEntire(trimmed) }
            ?: patterns.firstNotNullOfOrNull { it.matchEntire(normalized) }
            ?: return L1Result.Unrecognized

        // Honest, calm, no false success. Uses the Error state (recognized-but-unsupported).
        return L1Result.Invalid("AURA can't capture screenshots. Press Power + Volume Down on your device.")
    }
}
