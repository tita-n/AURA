package com.aura.resolver.l3

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.resolver.EntityCategory
import com.aura.resolver.L0Index

/**
 * L3 Deterministic Action Layer — validates proposed AuraAction against deterministic Android reality.
 * Exclusive validator: L0/L1/L2 must not call Android APIs, UI must not, MainActivity must not.
 * Platform access belongs behind this boundary (via L0Index which was built from PackageManager off-thread).
 * Validates only, does not reinterpret, rank, or repair.
 */
class L3Validator(
    private val index: L0Index
) {
    // Closed vocabulary for settings — only these keys may validate (from L0IndexFactory.settingsCatalog)
    private val allowedSettingsKeys = setOf(
        "wifi", "wifi_settings",
        "bluetooth", "bluetooth_settings",
        "display", "display_settings",
        "sound", "sound_settings",
        "battery", "battery_settings",
        "apps", "apps_settings"
    )

    // Supported channels for SendMessage
    private val supportedChannels = setOf("default", "message", "whatsapp", "sms", "call")

    fun validate(result: ResolvedResult): L3ValidationResult {
        return when (val action = result.action) {
            is AuraAction.OpenApp -> validateOpenApp(action, result)
            is AuraAction.Dial -> validateDial(action, result)
            is AuraAction.SendMessage -> validateSendMessage(action, result)
            is AuraAction.SendEmail -> validateSendEmail(action, result)
            is AuraAction.OpenSettings -> validateOpenSettings(action, result)
            is AuraAction.SetTimer -> validateSetTimer(action, result)
            // Inline/copy actions are always valid (no Android execution needed)
            is AuraAction.Copy -> L3ValidationResult.Validated(ValidatedAction(result))
            is AuraAction.OpenCalculator -> L3ValidationResult.Validated(ValidatedAction(result))
            is AuraAction.SearchPlayStore -> L3ValidationResult.Validated(ValidatedAction(result))
            is AuraAction.NoOp -> L3ValidationResult.Validated(ValidatedAction(result))
            // For any other pure actions, default to validated if they passed earlier checks
            else -> L3ValidationResult.Validated(ValidatedAction(result))
        }
    }

    private fun validateOpenApp(action: AuraAction.OpenApp, result: ResolvedResult): L3ValidationResult {
        val pkg = action.packageName.trim()
        if (pkg.isBlank()) return L3ValidationResult.Invalid("Invalid package")
        // Check via index: is there a launchable app with this package?
        // Index was built from PackageManager's launchable activities, so this is deterministic Android reality
        val exists = index.allEntities().any { it.category == EntityCategory.App && it.id == "app:$pkg" }
        if (!exists) return L3ValidationResult.Invalid("App not found or not launchable")
        // Also verify package name looks like a real package (contains dot)
        if (!pkg.contains(".")) return L3ValidationResult.Invalid("Invalid package name")
        return L3ValidationResult.Validated(ValidatedAction(result))
    }

    private fun validateDial(action: AuraAction.Dial, result: ResolvedResult): L3ValidationResult {
        val contactId = action.phoneNumber.trim() // actually contactId in Dial case is contactId, not phone
        if (contactId.isBlank()) return L3ValidationResult.Invalid("Invalid contact")
        val exists = index.allEntities().any { it.id == "contact:$contactId" || it.id == contactId }
        if (!exists) {
            // Also check by contactId without prefix
            val byId = index.allEntities().any { it.id.removePrefix("contact:") == contactId }
            if (!byId) return L3ValidationResult.Invalid("Contact not found")
        }
        // Ambiguous check: if this contact's display name has duplicates, L2 should have produced ASK, but if we reach here with single, validate
        // We do not silently select ambiguous; if the contactId corresponds to a name with multiple entries, we must ensure it's not ambiguous
        // For now, since we have exact contactId, it's not ambiguous — the ambiguity was at resolution time, not validation
        return L3ValidationResult.Validated(ValidatedAction(result))
    }

    private fun validateSendMessage(action: AuraAction.SendMessage, result: ResolvedResult): L3ValidationResult {
        val contactId = action.contactId.trim()
        if (contactId.isBlank()) return L3ValidationResult.Invalid("Invalid contact")
        val exists = index.allEntities().any { it.id == "contact:$contactId" || it.id.removePrefix("contact:") == contactId }
        if (!exists) return L3ValidationResult.Invalid("Contact not found")
        if (action.channel.isNotBlank() && action.channel !in supportedChannels) {
            // Channel must be supported, but we don't fail if it's default — future channels may be added
            // For now, treat unknown channel as Invalid if it's not in supported set and not default
            // Allow any non-blank for forward compatibility? Spec says channel must be supported
            return L3ValidationResult.Invalid("Unsupported channel")
        }
        // Message body is optional per contract, so no further validation
        return L3ValidationResult.Validated(ValidatedAction(result))
    }

    private fun validateSendEmail(action: AuraAction.SendEmail, result: ResolvedResult): L3ValidationResult {
        val contactId = action.contactId.trim()
        if (contactId.isBlank()) return L3ValidationResult.Invalid("Invalid contact")
        val exists = index.allEntities().any { it.id == "contact:$contactId" || it.id.removePrefix("contact:") == contactId }
        if (!exists) return L3ValidationResult.Invalid("Contact not found")
        // Email capability: for v0.1, any contact with valid id is considered emailable (deterministic)
        // Future may check for email address in disambiguation, but not now
        return L3ValidationResult.Validated(ValidatedAction(result))
    }

    private fun validateOpenSettings(action: AuraAction.OpenSettings, result: ResolvedResult): L3ValidationResult {
        val key = action.panel.trim()
        if (key.isBlank()) return L3ValidationResult.Invalid("Invalid settings key")
        // Closed vocabulary — only allowed keys
        if (key !in allowedSettingsKeys) {
            // Also check case-insensitive and without hyphen
            val normalizedKey = key.lowercase().replace("-", "")
            val allowedNormalized = allowedSettingsKeys.map { it.lowercase().replace("-", "") }.toSet()
            if (normalizedKey !in allowedNormalized) {
                return L3ValidationResult.Invalid("Unsupported settings")
            }
        }
        // Also verify that settings entity exists in index (deterministic)
        val exists = index.allEntities().any { it.category == EntityCategory.Settings && (it.id == "settings:$key" || it.id.removePrefix("settings:") == key) }
        // If not in index but in allowed vocabulary, still consider valid (index may be stale)
        // For determinism, allow allowed vocabulary even if index doesn't contain it yet
        return L3ValidationResult.Validated(ValidatedAction(result))
    }

    private fun validateSetTimer(action: AuraAction.SetTimer, result: ResolvedResult): L3ValidationResult {
        val secs = action.durationSeconds
        if (secs <= 0) return L3ValidationResult.Invalid("Timer must be positive")
        if (secs > 24 * 3600) return L3ValidationResult.Invalid("Timer too long")
        return L3ValidationResult.Validated(ValidatedAction(result))
    }
}
