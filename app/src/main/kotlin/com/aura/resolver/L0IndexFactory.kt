package com.aura.resolver

import com.aura.domain.ActionChipData
import com.aura.domain.AuraAction
import com.aura.domain.ResultType

/**
 * Factory for L0 index — deterministic, no Android APIs in domain/resolver.
 * Platform layer calls these helpers with data obtained via PackageManager/ContactsContract,
 * but the helpers themselves remain pure.
 */
object L0IndexFactory {

    fun appEntity(
        packageName: String,
        label: String
    ): IndexedEntity {
        val normalized = Normalizer.normalize(label)
        return IndexedEntity(
            id = "app:$packageName",
            displayLabel = label,
            normalizedLabel = normalized,
            category = EntityCategory.App,
            resultType = ResultType.App,
            action = AuraAction.OpenApp(packageName),
            disambiguation = packageName,
            subtitle = null
        )
    }

    fun contactEntity(
        contactId: String,
        displayName: String,
        disambiguation: String, // e.g., "Work · WhatsApp" or "Family" — never raw phone alone
        subtitle: String? = disambiguation
    ): IndexedEntity {
        val normalized = Normalizer.normalize(displayName)
        return IndexedEntity(
            id = "contact:$contactId",
            displayLabel = displayName,
            normalizedLabel = normalized,
            category = EntityCategory.Contact,
            resultType = ResultType.Contact,
            action = AuraAction.SendMessage(contactId, "default"),
            disambiguation = disambiguation,
            subtitle = subtitle,
            actionChips = listOf(
                ActionChipData("message", "Message"),
                ActionChipData("call", "Call")
            )
        )
    }

    fun settingsEntity(
        key: String,
        label: String,
        subtitle: String? = null
    ): IndexedEntity {
        val normalized = Normalizer.normalize(label)
        return IndexedEntity(
            id = "settings:$key",
            displayLabel = label,
            normalizedLabel = normalized,
            category = EntityCategory.Settings,
            resultType = ResultType.Settings,
            action = AuraAction.OpenSettings(key),
            disambiguation = subtitle,
            subtitle = subtitle
        )
    }

    /**
     * Build a demo index with realistic sample data for UI wiring and previews.
     * This is the v0.1 in-memory index — platform will replace with real PackageManager data
     * via L0IndexFactory.build(platformApps + platformContacts + settingsCatalog).
     */
    fun demoIndex(): L0Index {
        val entities = mutableListOf<IndexedEntity>()
        // Apps
        entities += appEntity("com.android.chrome", "Chrome")
        entities += appEntity("com.whatsapp", "WhatsApp")
        entities += appEntity("com.google.android.gm", "Gmail")
        entities += appEntity("com.spotify.music", "Spotify")
        entities += appEntity("com.example.calculator", "Calculator")
        // Contacts — note duplicate "Sarah" to exercise ASK
        entities += contactEntity("1", "Sarah", "sarah.okafor@email.com")
        entities += contactEntity("2", "Sarah", "called yesterday")
        entities += contactEntity("3", "Sarah M.", "mobile")
        entities += contactEntity("4", "Dad", "mobile")
        entities += contactEntity("5", "Mum", "mobile")
        // Settings — deterministic only (include both base and "settings" suffixed forms for L1)
        entities += settingsEntity("wifi", "Wi-Fi", "Network")
        entities += settingsEntity("wifi_settings", "Wi-Fi settings", "Network")
        entities += settingsEntity("bluetooth", "Bluetooth")
        entities += settingsEntity("bluetooth_settings", "Bluetooth settings")
        entities += settingsEntity("display", "Display")
        entities += settingsEntity("display_settings", "Display settings")
        entities += settingsEntity("sound", "Sound")
        entities += settingsEntity("sound_settings", "Sound settings")
        return L0Index.build(entities)
    }

    fun settingsCatalog(): List<IndexedEntity> = listOf(
        settingsEntity("wifi", "Wi-Fi", "Network"),
        settingsEntity("wifi_settings", "Wi-Fi settings", "Network"),
        settingsEntity("bluetooth", "Bluetooth"),
        settingsEntity("display", "Display"),
        settingsEntity("sound", "Sound"),
        settingsEntity("battery", "Battery"),
        settingsEntity("apps", "Apps")
    )
}
