package com.aura.platform.android

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Widget host coordinator — thin wrapper over [AppWidgetManager] + [android.appwidget.AppWidgetHost].
 *
 * The actual view lifecycle lives here (allocate/bind/createView/delete).
 * Discovery (filtered to HOME_SCREEN category per spec) is pure filtering.
 *
 * Ids follow the same lifecycle as AURA's persisted [com.aura.home.HomeSettings.widgetIds].
 * Stale ids pruned on load against [AppWidgetManager.getAppWidgetIds]; orphans deleted from the host.
 */
class AuraWidgetHost(private val context: Context) {

    companion object {
        const val HOST_ID: Int = 0x41555241 // "AURA"
        const val EXTRA_APPWIDGET_ID: String = AppWidgetManager.EXTRA_APPWIDGET_ID
    }

    private val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val host: android.appwidget.AppWidgetHost = android.appwidget.AppWidgetHost(context, HOST_ID)

    private val _installed: MutableStateFlow<List<AppWidgetProviderInfo>> = MutableStateFlow(emptyList())
    val installed: StateFlow<List<AppWidgetProviderInfo>> = _installed

    /** Host listening — must mirror Activity onStart/onStop. */
    fun startListening() {
        try { host.startListening() } catch (_: Exception) {}
        refreshInstalled()
    }

    fun stopListening() {
        try { host.stopListening() } catch (_: Exception) {}
    }

    fun refreshInstalled() {
        val all: List<AppWidgetProviderInfo> = try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                manager.getInstalledProvidersForProfile(null)
            } else {
                @Suppress("DEPRECATION") manager.installedProviders
            }
        } catch (_: Exception) { emptyList() }
        _installed.value = all.filter { isHomeWidget(it) }
            .sortedBy { providerLabel(it).lowercase() }
    }

    /** HOME_SCREEN category or unset (0) — deterministic rule per PRODUCT.md. */
    fun isHomeWidget(info: AppWidgetProviderInfo): Boolean {
        val cat = try { info.widgetCategory } catch (_: Exception) { 0 }
        return cat == 0 || (cat and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0
    }

    fun providerLabel(info: AppWidgetProviderInfo): String =
        try { info.loadLabel(context.packageManager) } catch (_: Exception) { info.provider.packageName }

    fun allocateId(): Int =
        try { host.allocateAppWidgetId() } catch (_: Exception) { AppWidgetManager.INVALID_APPWIDGET_ID }

    fun deleteId(id: Int) {
        try { host.deleteAppWidgetId(id) } catch (_: Exception) {}
    }

    /** Try direct bind; returns true if allowed without user grant. */
    fun bindIfAllowed(id: Int, provider: ComponentName): Boolean =
        try { manager.bindAppWidgetIdIfAllowed(id, provider) } catch (_: Exception) { false }

    /** Intent for the permission-grant bind flow (ACTION_APPWIDGET_BIND). Caller starts this for result. */
    fun bindPermissionIntent(id: Int, provider: ComponentName): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(EXTRA_APPWIDGET_ID, id)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }

    /** Configure intent for providers that require it — otherwise null. */
    fun configureIntent(info: AppWidgetProviderInfo, id: Int): Intent? {
        val cls = info.configure ?: return null
        return try {
            Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(cls).putExtra(EXTRA_APPWIDGET_ID, id)
        } catch (_: Exception) { null }
    }

    fun createView(id: Int, info: AppWidgetProviderInfo): android.appwidget.AppWidgetHostView? =
        try { host.createView(context, id, info) } catch (_: Exception) { null }

    fun hostIds(): List<Int> =
        try { host.appWidgetIds?.toList() ?: emptyList() } catch (_: Exception) { emptyList() }

    /**
     * Report actual view size to the widget so responsive widgets pick the right layout.
     * Call after the host view has measured.
     */
    fun updateSize(hostView: android.appwidget.AppWidgetHostView, widthDp: Int, heightDp: Int) {
        try {
            val opts = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            }
            hostView.updateAppWidgetSize(opts, widthDp, widthDp, heightDp, heightDp)
        } catch (_: Exception) {}
    }

    /** Delete ids that are allocated in the host but not in stored settings (orphans). */
    fun deleteOrphaned(storedIds: List<Int>) {
        val orphans = hostIds().filter { it !in storedIds }
        orphans.forEach { deleteId(it) }
    }

    /**
     * Look up [AppWidgetProviderInfo] for a persisted [id].
     * Returns null for stale ids (provider uninstalled) — caller should prune.
     */
    fun providerForId(id: Int): AppWidgetProviderInfo? =
        try { manager.getAppWidgetInfo(id) } catch (_: Exception) { null }
}
