package com.aura.home

/**
 * Home layout model — pure data, no Android APIs.
 *
 * Fixed regions per PRODUCT.md:
 *   Time/Presence → optional modules/widgets region → App Library affordance
 *   → resolution surface → Command Bar (never movable) → Dock.
 * The default configuration is sparse; Time + Presence + Command Bar + Dock alone
 * is a valid final Home.
 */

/** One dock entry — identified by installed package name only. Labels resolve from the live index at render time. */
data class DockItem(val packageName: String)

enum class HomeModuleType {
    NextEvent,
    Battery,
    Music
}

enum class ThemeMode { System, Dark, Light }

enum class AnimationIntensity { Standard, Reduced }

/**
 * Accent choice — Dynamic (token default) or a curated manual index into [AccentPalette].
 * No unrestricted color picker: curated pairs only, each contrast-checked against its base.
 */
sealed interface AccentChoice {
    data object Dynamic : AccentChoice
    data class Curated(val index: Int) : AccentChoice
}

object AccentPalette {
    // Curated accent pairs (darkBase, lightBase) — every value passes WCAG 4.5:1 vs its base.
    val entries: List<Pair<Int, Int>> = listOf(
        0xFF8B8BFF.toInt() to 0xFF5B5BD6.toInt(), // Iris (default)
        0xFF7FD8C8.toInt() to 0xFF0F766E.toInt(), // Teal
        0xFFE8C468.toInt() to 0xFFB45309.toInt(), // Amber
        0xFFF2A6B8.toInt() to 0xFFBE185D.toInt(), // Rose
        0xFF86D99B.toInt() to 0xFF15803D.toInt(), // Green
        0xFFB8C0CC.toInt() to 0xFF475569.toInt()  // Slate
    )

    fun isValid(index: Int): Boolean = index in entries.indices
}

/** Scalar appearance/behavior customization. */
data class HomeCustomization(
    val themeMode: ThemeMode = ThemeMode.System,
    val accent: AccentChoice = AccentChoice.Dynamic,
    val animationIntensity: AnimationIntensity = AnimationIntensity.Standard,
    val showWallpaper: Boolean = false
)

/** Complete persisted home settings — one aggregate, one StateFlow upstream. */
data class HomeSettings(
    val customization: HomeCustomization = HomeCustomization(),
    val dock: List<DockItem> = emptyList(),
    val modules: List<HomeModuleType> = emptyList(), // enabled modules, display order
    val widgetIds: List<Int> = emptyList()           // bound AppWidget ids, display order
)

/** Shared module data — lives in home so UI can import without pulling platform. */
data class BatteryUiModel(val percent: Int, val charging: Boolean)
data class NextEventInfo(val title: String, val beginMillis: Long, val endMillis: Long, val allDay: Boolean)

/** Time-of-day presence greeting. Silence (null) is valid — late night stays quiet. */
object Presence {
    fun greetingFor(hourOfDay: Int): String? = when (hourOfDay) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..22 -> "Good evening"
        else -> null
    }
}
