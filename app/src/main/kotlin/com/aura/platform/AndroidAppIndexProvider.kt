package com.aura.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import com.aura.resolver.AppIndexSource
import com.aura.resolver.IndexedEntity
import com.aura.resolver.L0IndexFactory

/**
 * Platform boundary — the ONLY place PackageManager is imported.
 * Domain/resolver never imports this. UI never calls this directly.
 * For v0.1, this is available but not yet wired into the hot path;
 * L0IndexFactory.demoIndex() is used until the permission/index-refresh lifecycle is added.
 * When wired, this will be called on a background thread, never on keystroke.
 */
class AndroidAppIndexProvider(
    private val context: Context
) : AppIndexSource {
    /**
     * Load installed launchable apps — MUST be called off the main/UI thread.
     * Uses PackageManager query with launcher intent; respects <queries> visibility.
     * Only includes apps with a launcher activity (actually launchable).
     */
    override fun getAppEntities(): List<IndexedEntity> = loadApps()

    fun loadApps(): List<IndexedEntity> {
        // Enforce off-main-thread for safety; still works if caller mistakenly uses main thread but warns via check
        // We do not throw, but document contract: caller must use Dispatchers.IO
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Soft warning — not throwing to avoid crashing if called incorrectly in tests, but log
            android.util.Log.w("AURA", "AndroidAppIndexProvider.loadApps() called on main thread — must be off-main-thread")
        }
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(intent, 0)
        return apps.mapNotNull { info ->
            val label = info.loadLabel(pm)?.toString() ?: return@mapNotNull null
            val packageName = info.activityInfo.packageName ?: return@mapNotNull null
            if (label.isBlank() || packageName.isBlank()) return@mapNotNull null
            L0IndexFactory.appEntity(packageName, label)
        }
    }
}
