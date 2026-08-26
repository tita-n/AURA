package com.aura.ui.library

import com.aura.domain.ResultType
import com.aura.resolver.EntityCategory
import com.aura.resolver.IndexedEntity
import com.aura.resolver.Normalizer
import java.util.Locale

/**
 * Pure App Library logic — no Compose, no Android APIs. Fully unit-testable.
 * Derives everything from the existing L0 index entities; never a second catalog.
 */
object AppLibraryLogic {

    data class Section(val letter: String, val startIndex: Int)

    /** Launchable apps only, deterministic alphabetical order (normalized label, then package id tiebreak).
     *  Uses Normalizer parity with L0 so " Chrome " and "CHROME" sort identically. */
    fun appsFromIndex(entities: List<IndexedEntity>): List<IndexedEntity> =
        entities
            .filter { it.category == EntityCategory.App && it.resultType == ResultType.App }
            .sortedWith(
                compareBy(
                    { it.normalizedLabel },
                    { it.id } // stable package identifier tiebreak for duplicate labels
                )
            )

    /** Live search over the already-loaded list — no platform enumeration per keystroke.
     *  Uses Normalizer parity with L0 (NFC, trim, whitespace collapse, lowercase ROOT)
     *  so Command Bar and App Library feel like the same engine. */
    fun filter(apps: List<IndexedEntity>, query: String): List<IndexedEntity> {
        val q = Normalizer.normalize(query)
        if (q.isEmpty()) return apps
        return apps.filter {
            it.normalizedLabel.contains(q) || it.id.removePrefix("app:").lowercase(Locale.ROOT).contains(q)
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

/**
 * Pure App Library rail ↔ list coupling. Keeps all scroll/position math out of the
 * Composable so it can be unit-tested independently of LazyListState.
 *
 * The list has a non-app header item (search field) at index 0, so a filtered-list
 * index (section.startIndex) is offset by [HEADER_OFFSET] from the LazyList index.
 */
object AppLibraryRail {

    const val HEADER_OFFSET = 1

    /** Index (into [sections]) whose section is currently at/above [filteredVisibleIndex]. */
    fun activeSectionIndex(sections: List<AppLibraryLogic.Section>, filteredVisibleIndex: Int): Int {
        if (sections.isEmpty()) return 0
        var idx = 0
        for (i in sections.indices) {
            if (sections[i].startIndex <= filteredVisibleIndex) idx = i else break
        }
        return idx
    }

    /** LazyList index (including header) that should be scrolled to for [sectionIndex]. */
    fun listScrollIndex(sectionIndex: Int): Int = sectionIndex + HEADER_OFFSET

    /**
     * Resolve a rail letter to the filtered-list index to scroll to.
     * Exact match first; otherwise the nearest available section (by letter order),
     * so missing letters still land somewhere sensible.
     */
    fun targetIndexForLetter(sections: List<AppLibraryLogic.Section>, letter: String): Int? {
        if (sections.isEmpty()) return null
        sections.firstOrNull { it.letter == letter }?.let { return it.startIndex }
        val target = letter.firstOrNull()?.uppercaseChar() ?: return sections.first().startIndex
        val rank: (Char) -> Int = { c -> if (c == '#') 'A'.code - 1 else c.code }
        val tr = rank(target)
        var best = sections.first()
        var bestDist = Int.MAX_VALUE
        for (s in sections) {
            val sr = rank(s.letter.firstOrNull() ?: '#')
            val d = kotlin.math.abs(sr - tr)
            if (d < bestDist) { bestDist = d; best = s }
        }
        return best.startIndex
    }
}
