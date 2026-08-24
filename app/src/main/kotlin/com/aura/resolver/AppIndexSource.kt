package com.aura.resolver

/**
 * Narrow abstraction for app indexing — allows resolver tests without PackageManager.
 * Platform implementation (AndroidAppIndexProvider) is the only place that touches PackageManager.
 */
interface AppIndexSource {
    fun getAppEntities(): List<IndexedEntity>
}
