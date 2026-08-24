package com.aura.resolver

import com.aura.domain.ActionChipData
import com.aura.domain.AuraAction
import com.aura.domain.ResultType

/**
 * Clean internal abstraction for L0 indexed entities.
 * Contains only deterministic data needed for resolution and eventual execution.
 * No Android Intent, no ContactsContract, no PackageManager here.
 */
data class IndexedEntity(
    val id: String, // stable identifier: packageName for apps, contactId for contacts, key for settings
    val displayLabel: String, // original label as shown to user (e.g., "Chrome", "Sarah")
    val normalizedLabel: String, // Normalizer.normalize(displayLabel) — cached at index time
    val category: EntityCategory,
    val resultType: ResultType,
    val action: AuraAction,
    // For disambiguation in ASK candidate rows — never raw phone as sole fact
    val disambiguation: String? = null,
    val subtitle: String? = null,
    val actionChips: List<ActionChipData> = emptyList()
)

enum class EntityCategory {
    App,
    Contact,
    Settings,
    Shortcut
}

fun IndexedEntity.matchesPrefix(normalizedQuery: String): Boolean {
    return normalizedLabel.startsWith(normalizedQuery)
}

fun IndexedEntity.matchesExact(normalizedQuery: String): Boolean {
    return normalizedLabel == normalizedQuery
}
