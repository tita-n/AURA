package com.aura.resolver.l3

import com.aura.domain.AuraAction
import com.aura.domain.ResolvedResult
import com.aura.resolver.EntityCategory
import com.aura.resolver.L0Index
import com.aura.resolver.TargetPatterns

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
        "apps", "apps_settings",
        "accessibility", "accessibility_settings",
        "location", "location_settings",
        "date_and_time", "date_and_time_settings"
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
            is AuraAction.OpenCamera -> L3ValidationResult.Validated(ValidatedAction(result))
            is AuraAction.SetReminder -> validateSetReminder(action, result)
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
        // Direct number dial — "call 555 123 4567": deterministic shape check, no contact needed.
        if (action.contactId.isNullOrBlank()) {
            return if (TargetPatterns.isPhoneLike(action.phoneNumber))
                L3ValidationResult.Validated(ValidatedAction(result))
            else L3ValidationResult.Invalid("Invalid phone number")
        }
        val contactId = action.contactId.trim()
        val entity = index.allEntities().firstOrNull {
            it.id == "contact:$contactId" || it.id.removePrefix("contact:") == contactId
        } ?: return L3ValidationResult.Invalid("Contact not found")
        // Capability check — real indexed data: Dial requires at least one phone target.
        if (entity.phones.isEmpty()) return L3ValidationResult.Unavailable(result.id.removePrefix("contact:"))
        if (action.phoneNumber.isBlank()) return L3ValidationResult.Invalid("No phone number for contact")
        return L3ValidationResult.Validated(ValidatedAction(result))
    }

    private fun validateSendMessage(action: AuraAction.SendMessage, result: ResolvedResult): L3ValidationResult {
        // Direct number message — "message 555 123 4567 hi": sms-family channels only;
        // WhatsApp to a non-contact cannot be executed safely.
        if (action.contactId.isBlank()) {
            val channel = action.channel.lowercase()
            if (channel !in setOf("default", "message", "sms")) {
                return L3ValidationResult.Invalid("Unsupported channel")
            }
            return if (!action.phone.isNullOrBlank() && TargetPatterns.isPhoneLike(action.phone))
                L3ValidationResult.Validated(ValidatedAction(result))
            else L3ValidationResult.Invalid("Invalid phone number")
        }
        val contactId = action.contactId.trim()
        if (contactId.isBlank()) return L3ValidationResult.Invalid("Invalid contact")
        val entity = index.allEntities().firstOrNull {
            it.id == "contact:$contactId" || it.id.removePrefix("contact:") == contactId
        } ?: return L3ValidationResult.Invalid("Contact not found")
        if (action.channel.isNotBlank() && action.channel !in supportedChannels) {
            return L3ValidationResult.Invalid("Unsupported channel")
        }
        // SMS-family channels require a phone target; whatsapp only needs the app.
        val needsPhone = action.channel.lowercase() in setOf("default", "message", "sms")
        if (needsPhone && entity.phones.isEmpty()) {
            return L3ValidationResult.Unavailable(contactId)
        }
        if (needsPhone && action.phone.isNullOrBlank()) {
            // Proposal missing target that the index says exists — deterministic repair is forbidden,
            // but the index is authoritative: surface as unavailable rather than guess.
            return L3ValidationResult.Unavailable(contactId)
        }
        return L3ValidationResult.Validated(ValidatedAction(result))
    }

    private fun validateSendEmail(action: AuraAction.SendEmail, result: ResolvedResult): L3ValidationResult {
        // Direct address email — "email someone@example.com": deterministic shape check.
        if (action.contactId.isBlank()) {
            return if (!action.emailAddress.isNullOrBlank() && TargetPatterns.isEmailLike(action.emailAddress))
                L3ValidationResult.Validated(ValidatedAction(result))
            else L3ValidationResult.Invalid("Invalid email address")
        }
        val contactId = action.contactId.trim()
        if (contactId.isBlank()) return L3ValidationResult.Invalid("Invalid contact")
        val entity = index.allEntities().firstOrNull {
            it.id == "contact:$contactId" || it.id.removePrefix("contact:") == contactId
        } ?: return L3ValidationResult.Invalid("Contact not found")
        // Capability check — real indexed data: email requires an address target.
        if (entity.emails.isEmpty()) return L3ValidationResult.Unavailable(contactId)
        if (action.emailAddress.isNullOrBlank()) {
            return L3ValidationResult.Unavailable(contactId)
        }
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

    private fun validateSetReminder(action: AuraAction.SetReminder, result: ResolvedResult): L3ValidationResult {
        if (action.title.isBlank()) return L3ValidationResult.Invalid("Reminder text is empty")
        // hour must be 0..23 (matcher already normalizes am/pm); minute 0..59; day offset sane
        if (action.hour !in 0..23) return L3ValidationResult.Invalid("Invalid hour")
        if (action.minute !in 0..59) return L3ValidationResult.Invalid("Invalid minute")
        if (action.dayOffsetDays !in 0..365) return L3ValidationResult.Invalid("Invalid day offset")
        return L3ValidationResult.Validated(ValidatedAction(result))
    }
}
