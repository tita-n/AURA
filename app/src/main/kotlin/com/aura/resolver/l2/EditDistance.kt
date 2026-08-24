package com.aura.resolver.l2

/**
 * Deterministic Levenshtein edit distance for typo tolerance.
 * Threshold small (1-2) to keep deterministic and fast.
 * No ML, no embeddings.
 */
object EditDistance {
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[a.length][b.length]
    }

    fun isFuzzyMatch(query: String, target: String, maxDistance: Int = 2): Boolean {
        if (query == target) return true
        // Early reject if length diff > maxDistance
        if (kotlin.math.abs(query.length - target.length) > maxDistance) return false
        return distance(query, target) <= maxDistance
    }

    /**
     * Find entities whose normalized label is within edit distance threshold.
     * Used for typo tolerance (e.g., "chrom" -> "chrome").
     */
    fun fuzzyFilter(query: String, candidates: List<String>, threshold: Int = 2): List<String> {
        return candidates.filter { isFuzzyMatch(query, it, threshold) }
    }
}
