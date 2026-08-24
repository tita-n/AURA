package com.aura.ui.library

import com.aura.domain.ResultType
import com.aura.resolver.EntityCategory
import com.aura.resolver.IndexedEntity

/**
 * Pure App Library logic — no Compose, no Android APIs. Fully unit-testable.
 * Derives everything from the existing L0 index entities; never a second catalog.
 */
object AppLibraryLogic {

    data class Section(val letter: String, val startIndex: Int)

    /** Launchable apps only, deterministic alphabetical order (label, then package id tiebreak). */
    fun appsFromIndex(entities: List<IndexedEntity>): List<IndexedEntity> =
        entities
            .filter { it.category == EntityCategory.App && it.resultType == ResultType.App }
            .sortedWith(
                compareBy(
                    { it.displayLabel.lowercase() },
                    { it.normalizedLabel },
                    { it.id } // stable package identifier tiebreak for duplicate labels
                )
            )

    /** Live search over the already-loaded list — no platform enumeration per keystroke. */
    fun filter(apps: List<IndexedEntity>, query: String): List<IndexedEntity> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return apps
        return apps.filter {
            it.normalizedLabel.contains(q) || it.id.removePrefix("app:").lowercase().contains(q)
        }
    }

    /**
     * Alphabetical sections derived from the sorted list.
     * Labels beginning with digits or symbols group under "#".
     */
    fun sections(sortedApps: List<IndexedEntity>): List<Section> {
        if (sortedApps.isEmpty()) return emptyList()
        val out = mutableListOf<Section>()
        var current: String? = null
        sortedApps.forEachIndexed { idx, app ->
            val letter = sectionLetterFor(app.displayLabel)
            if (letter != current) {
                out += Section(letter, idx)
                current = letter
            }
        }
        return out
    }

    fun sectionLetterFor(label: String): String {
        val c = label.trim().firstOrNull()?.uppercaseChar() ?: return "#"
        return if (c in 'A'..'Z') c.toString() else "#"
    }

    /** Rail letters in stable order: '#' first, then A..Z present in the list. */
    fun railLetters(sections: List<Section>): List<Section> =
        sections.sortedWith(compareBy({ it.letter != "#" }, { it.letter }))
}
