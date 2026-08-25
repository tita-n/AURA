package com.aura.home

/**
 * Compact per-key codecs for SharedPreferences storage.
 * Hand-rolled (no kotlinx.serialization): deterministic, testable on JVM without Android json stubs.
 *
 * Format: deliberately tiny. Unknown values degrade gracefully on load (unknown enums fallback to defaults).
 */

/** Pure codec helpers — shared by AuraPrefs and its tests. */
object HomeCodecs {

    // ---- Dock ---------------------------------------------------------------

    /** Join package names with "|". Empty dock -> "". */
    fun encodeDock(dock: List<DockItem>): String =
        dock.joinToString("|") { it.packageName }

    fun decodeDock(raw: String?): List<DockItem> =
        if (raw.isNullOrBlank()) emptyList()
        else raw.split("|").filter { it.isNotBlank() }.map { DockItem(it) }

    // ---- Modules ------------------------------------------------------------

    fun encodeModules(modules: List<HomeModuleType>): String =
        modules.joinToString("|") { it.name }

    fun decodeModules(raw: String?): List<HomeModuleType> =
        if (raw.isNullOrBlank()) emptyList()
        else raw.split("|").mapNotNull { token ->
            when (token) {
                HomeModuleType.NextEvent.name -> HomeModuleType.NextEvent
                HomeModuleType.Battery.name -> HomeModuleType.Battery
                HomeModuleType.Music.name -> HomeModuleType.Music
                else -> null
            }
        }

    // ---- Theme mode ---------------------------------------------------------

    fun encodeThemeMode(mode: ThemeMode): String = when (mode) {
        ThemeMode.System -> "S"
        ThemeMode.Dark -> "D"
        ThemeMode.Light -> "L"
    }

    fun decodeThemeMode(raw: String?): ThemeMode =
        when (raw) {
            "D" -> ThemeMode.Dark
            "L" -> ThemeMode.Light
            else -> ThemeMode.System
        }

    // ---- Accent -------------------------------------------------------------

    fun encodeAccent(accent: AccentChoice): String = when (accent) {
        is AccentChoice.Dynamic -> "DYN"
        is AccentChoice.Curated -> "C:${accent.index}"
    }

    fun decodeAccent(raw: String?): AccentChoice {
        if (raw.isNullOrBlank() || raw == "DYN") return AccentChoice.Dynamic
        if (raw.startsWith("C:")) {
            val idx = raw.substring(2).toIntOrNull() ?: return AccentChoice.Dynamic
            return if (AccentPalette.isValid(idx)) AccentChoice.Curated(idx) else AccentChoice.Dynamic
        }
        return AccentChoice.Dynamic
    }

    // ---- Animation intensity ------------------------------------------------

    fun encodeAnimation(intensity: AnimationIntensity): String =
        if (intensity == AnimationIntensity.Reduced) "R" else "S"

    fun decodeAnimation(raw: String?): AnimationIntensity =
        if (raw == "R") AnimationIntensity.Reduced else AnimationIntensity.Standard

    // ---- Customization (one line per setting family — 4 keys total + dock/modules/widgets)
    // Keys are stable SharedPreferences keys defined in AuraPrefs.

    fun encodeCustom(custom: HomeCustomization): Map<String, String> = mapOf(
        AuraPrefsKeys.KEY_THEME to encodeThemeMode(custom.themeMode),
        AuraPrefsKeys.KEY_ACCENT to encodeAccent(custom.accent),
        AuraPrefsKeys.KEY_ANIM to encodeAnimation(custom.animationIntensity),
        AuraPrefsKeys.KEY_WP to if (custom.showWallpaper) "1" else "0"
    )

    fun decodeCustom(read: (String) -> String?): HomeCustomization = HomeCustomization(
        themeMode = decodeThemeMode(read(AuraPrefsKeys.KEY_THEME)),
        accent = decodeAccent(read(AuraPrefsKeys.KEY_ACCENT)),
        animationIntensity = decodeAnimation(read(AuraPrefsKeys.KEY_ANIM)),
        showWallpaper = read(AuraPrefsKeys.KEY_WP) == "1"
    )

    // ---- Widget ids (ints) --------------------------------------------------

    fun encodeWidgetIds(ids: List<Int>): String =
        ids.joinToString(",")

    fun decodeWidgetIds(raw: String?): List<Int> =
        if (raw.isNullOrBlank()) emptyList()
        else raw.split(",").mapNotNull { it.trim().toIntOrNull() }

    /**
     * Prune persisted ids against live host ids: drop stale apps, keep ordering of stored list.
     * Orphans (live ids not in stored list) are intentionally not returned — callers
     * should call [AuraWidgetHost.deleteOrphaned] for those. Pure part is tested here.
     */
    fun pruneStoredWidgetIds(stored: List<Int>, liveHostIds: List<Int>): List<Int> =
        stored.filter { it in liveHostIds }
}

/** Placeholder so HomeCodecs can reference stable pref keys without a forward import cycle. */
object AuraPrefsKeys {
    const val KEY_DOCK = "home.dock"
    const val KEY_MODULES = "home.modules"
    const val KEY_WIDGETS = "home.widgets"
    const val KEY_THEME = "home.theme"
    const val KEY_ACCENT = "home.accent"
    const val KEY_ANIM = "home.anim"
    const val KEY_WP = "home.wallpaper"
}
