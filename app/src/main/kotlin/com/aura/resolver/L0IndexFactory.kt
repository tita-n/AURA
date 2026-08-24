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

    fun demoApps(): List<IndexedEntity> = listOf(
        appEntity("com.android.chrome", "Chrome"),
        appEntity("com.whatsapp", "WhatsApp"),
        appEntity("com.google.android.gm", "Gmail"),
        appEntity("com.spotify.music", "Spotify"),
        appEntity("com.example.calculator", "Calculator")
    )

    fun demoContacts(): List<IndexedEntity> = listOf(
        contactEntity("1", "Sarah", "sarah.okafor@email.com"),
        contactEntity("2", "Sarah", "called yesterday"),
        contactEntity("3", "Sarah M.", "mobile"),
        contactEntity("4", "Dad", "mobile"),
        contactEntity("5", "Mum", "mobile")
    )

    fun demoSettings(): List<IndexedEntity> = listOf(
        settingsEntity("wifi", "Wi-Fi", "Network"),
        settingsEntity("wifi_settings", "Wi-Fi settings", "Network"),
        settingsEntity("bluetooth", "Bluetooth"),
        settingsEntity("bluetooth_settings", "Bluetooth settings"),
        settingsEntity("display", "Display"),
        settingsEntity("display_settings", "Display settings"),
        settingsEntity("sound", "Sound"),
        settingsEntity("sound_settings", "Sound settings")
    )

    /**
     * Build a demo index with realistic sample data for UI wiring and previews.
     * This is the v0.1 in-memory index — platform will replace demoApps with real apps.
     */
    fun demoIndex(): L0Index {
        val entities = mutableListOf<IndexedEntity>()
        entities += demoApps()
        entities += demoContacts()
        entities += demoSettings()
        return L0Index.build(entities)
    }

    /**
     * Build index from composable sources — allows real apps to replace demo apps
     * while preserving contacts/settings. Example: realApps + demoContacts() + demoSettings()
     */
    fun buildIndex(
        apps: List<IndexedEntity>,
        contacts: List<IndexedEntity> = demoContacts(),
        settings: List<IndexedEntity> = demoSettings()
    ): L0Index {
        return L0Index.build(apps + contacts + settings)
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
