package com.aura.platform

import android.content.Context
import android.content.pm.PackageManager
import com.aura.resolver.IndexedEntity
import com.aura.resolver.L0IndexFactory
import com.aura.resolver.Normalizer

/**
 * Platform boundary — the ONLY place PackageManager is imported.
 * Domain/resolver never imports this. UI never calls this directly.
 * For v0.1, this is available but not yet wired into the hot path;
 * L0IndexFactory.demoIndex() is used until the permission/index-refresh lifecycle is added.
 * When wired, this will be called on a background thread, never on keystroke.
 */
class AndroidAppIndexProvider(
    private val context: Context
) {
    /**
     * Load installed launchable apps — must be called off the main thread.
     * Uses PackageManager query with launcher intent; respects <queries> visibility.
     */
    fun loadApps(): List<IndexedEntity> {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(intent, 0)
        return apps.mapNotNull { info ->
            val label = info.loadLabel(pm)?.toString() ?: return@mapNotNull null
            val packageName = info.activityInfo.packageName ?: return@mapNotNull null
            // Filter out non-launchable or system internals if needed — keep deterministic
            if (label.isBlank() || packageName.isBlank()) return@mapNotNull null
            L0IndexFactory.appEntity(packageName, label)
        }
    }

    /**
     * Example of future incremental refresh — not used in v0.1 hot path.
     * Will be triggered via PackageManager broadcast / WorkManager.
     */
    fun loadAppsAsync(onResult: (List<IndexedEntity>) -> Unit) {
        // Placeholder for background loading — will use coroutine/WorkManager in v0.2
        onResult(loadApps())
    }
}
