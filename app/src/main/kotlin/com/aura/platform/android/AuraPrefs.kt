package com.aura.platform.android

import android.content.Context
import android.content.SharedPreferences
import com.aura.home.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persistence decision — SharedPreferences (not Room).
 *
 * Why: [HomeSettings] is a handful of small ordered lists plus scalar settings.
 * No relational queries, no migrations beyond single-key codecs, no observed
 * relational index. SharedPreferences gives atomic key/value storage with a
 * single editor.apply() per change, zero dependencies, instantaneous cold start,
 * and trivial testability (codecs live in [HomeCodecs], which is pure).
 * A database would add build complexity, runtime overhead, and OEM fragmentation
 * risk for no meaningful query advantage. If the widget/layout model later
 * requires relational grouping (e.g., per-page free-form canvas), revisit Room.
 */
class AuraPrefs(context: Context) {

    companion object {
        const val PREFS_NAME = "aura_home"

        // Stable SharedPreferences keys — must never change without a codec migration.
        const val KEY_DOCK = AuraPrefsKeys.KEY_DOCK
        const val KEY_MODULES = AuraPrefsKeys.KEY_MODULES
        const val KEY_WIDGETS = AuraPrefsKeys.KEY_WIDGETS
        const val KEY_THEME = AuraPrefsKeys.KEY_THEME
        const val KEY_ACCENT = AuraPrefsKeys.KEY_ACCENT
        const val KEY_ANIM = AuraPrefsKeys.KEY_ANIM
        const val KEY_WP = AuraPrefsKeys.KEY_WP
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings: MutableStateFlow<HomeSettings> =
        MutableStateFlow(load())

    val settings: StateFlow<HomeSettings> = _settings

    private fun load(): HomeSettings {
        val custom = HomeCodecs.decodeCustom { k -> prefs.getString(k, null) }
        val dock = HomeCodecs.decodeDock(prefs.getString(KEY_DOCK, null))
        val modules = HomeCodecs.decodeModules(prefs.getString(KEY_MODULES, null))
        val widgets = HomeCodecs.decodeWidgetIds(prefs.getString(KEY_WIDGETS, null))
        return HomeSettings(customization = custom, dock = dock, modules = modules, widgetIds = widgets)
    }

    /** Save synchronously (apply is async on disk but immediate in-memory). */
    private fun save(settings: HomeSettings) {
        val m = HomeCodecs.encodeCustom(settings.customization)
        prefs.edit()
            .putString(KEY_THEME, m[KEY_THEME])
            .putString(KEY_ACCENT, m[KEY_ACCENT])
            .putString(KEY_ANIM, m[KEY_ANIM])
            .putString(KEY_WP, m[KEY_WP])
            .putString(KEY_DOCK, HomeCodecs.encodeDock(settings.dock))
            .putString(KEY_MODULES, HomeCodecs.encodeModules(settings.modules))
            .putString(KEY_WIDGETS, HomeCodecs.encodeWidgetIds(settings.widgetIds))
            .apply()
        _settings.value = settings
    }

    fun setCustomization(custom: HomeCustomization) {
        save(_settings.value.copy(customization = custom))
    }

    fun setDock(dock: List<DockItem>) {
        save(_settings.value.copy(dock = dock))
    }

    fun setModules(modules: List<HomeModuleType>) {
        save(_settings.value.copy(modules = modules))
    }

    fun setWidgetIds(ids: List<Int>) {
        save(_settings.value.copy(widgetIds = ids))
    }

    fun setSettings(settings: HomeSettings) {
        save(settings)
    }

    /** Force re-read from disk (used on process recreation / testing). */
    fun reload(): HomeSettings = load().also { _settings.value = it }
}
