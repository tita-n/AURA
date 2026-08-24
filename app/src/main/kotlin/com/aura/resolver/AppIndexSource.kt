package com.aura.resolver

/**
 * Narrow abstraction for app indexing — allows resolver tests without PackageManager.
 * Platform implementation (AndroidAppIndexProvider) is the only place that touches PackageManager.
 */
interface AppIndexSource {
    fun getAppEntities(): List<IndexedEntity>
}

/**
 * Narrow abstraction for contacts indexing — platform-only implementation touches ContactsContract.
 * Returns empty list when permission is unavailable (graceful degradation, never a fake error).
 */
interface ContactIndexSource {
    fun getContactEntities(): List<IndexedEntity>
}
