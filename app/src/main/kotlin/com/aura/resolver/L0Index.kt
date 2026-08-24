package com.aura.resolver

/**
 * In-memory L0 index — built once, queried cheaply on every keystroke.
 * Separates: data (entities), normalization (via Normalizer), lookup (exact/prefix).
 * No Android APIs, no allocations per query beyond list filtering.
 * Target <10ms for query — with ~200 entities, brute scan is <1ms; HashMap for exact is O(1).
 */
class L0Index private constructor(
    private val entities: List<IndexedEntity>,
    private val exactMap: Map<String, List<IndexedEntity>>
) {
    fun size(): Int = entities.size

    fun allEntities(): List<IndexedEntity> = entities

    /**
     * Lookup for a pre-normalized query.
     * Returns exact matches first; prefix only if no exact exists.
     */
    fun lookup(normalizedQuery: String): L0LookupResult {
        if (normalizedQuery.isEmpty()) return L0LookupResult.None
        val exact = exactMap[normalizedQuery]
        if (!exact.isNullOrEmpty()) {
            return L0LookupResult.Exact(exact)
        }
        // Prefix scan — linear but cheap for <500 entities; <10ms budget easily met
        val prefix = entities.filter { it.normalizedLabel.startsWith(normalizedQuery) }
        return if (prefix.isEmpty()) L0LookupResult.None else L0LookupResult.Prefix(prefix)
    }

    companion object {
        fun build(entities: List<IndexedEntity>): L0Index {
            // Pre-normalize at build time if not already? Entities already carry normalizedLabel,
            // but verify determinism by re-normalizing displayLabel and asserting equality in debug.
            val map = mutableMapOf<String, MutableList<IndexedEntity>>()
            for (e in entities) {
                map.getOrPut(e.normalizedLabel) { mutableListOf() }.add(e)
            }
            return L0Index(entities.toList(), map)
        }

        fun empty(): L0Index = L0Index(emptyList(), emptyMap())
    }
}

sealed interface L0LookupResult {
    data class Exact(val entities: List<IndexedEntity>) : L0LookupResult
    data class Prefix(val entities: List<IndexedEntity>) : L0LookupResult
    data object None : L0LookupResult
}
