package com.aura.resolver.l1

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.domain.ResultType

/**
 * Camera — deterministic patterns: "camera", "open camera", "launch camera", "take a photo",
 * "take a picture", "selfie".
 * Resolves to OpenCamera. AURA never requests the CAMERA permission; it hands off to the
 * system camera application, which owns its own permission. No custom camera is implemented.
 */
class CameraGrammar : L1Grammar {
    override fun name() = "Camera"

    // Bare "camera" is usually satisfied earlier by L0 (exact/prefix match on a Camera app),
    // so this grammar focuses on the verb/intent phrasings that L0 does not capture.
    private val patterns = listOf(
        Regex("""^\s*(?:open|launch|start|use)\s+camera\s*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*(?:take|snap|shoot)\s+(?:a\s+)?(?:photo|picture|pic|selfie)\s*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*(?:camera|selfie|photo|picture|pic)\s*$""", RegexOption.IGNORE_CASE)
    )

    override fun parse(normalized: String, raw: String): L1Result {
        val trimmed = raw.trim()
        val match = patterns.firstNotNullOfOrNull { it.matchEntire(trimmed) }
            ?: patterns.firstNotNullOfOrNull { it.matchEntire(normalized) }
            ?: return L1Result.Unrecognized

        return L1Result.Resolved(
            ResolvedResult(
                id = "camera:launch",
                title = "Open Camera",
                subtitle = "Launches your camera app",
                type = ResultType.Camera,
                action = AuraAction.OpenCamera()
            )
        )
    }
}
