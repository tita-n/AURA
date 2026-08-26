package com.aura.home

/**
 * Pure widget-limit and size-constraint logic — no Android APIs, fully unit-testable.
 *
 * AURA is sparse: at most ONE third-party widget is allowed, and it must not dominate
 * Home. Providers may still impose their own minimum dimensions; AURA rejects widgets
 * whose minimum size exceeds the allowed area and clamps resizable widgets to the cap.
 */
object WidgetLogic {

    /** AURA allows exactly one third-party widget. No widget wall. */
    const val MAX_WIDGETS = 1

    /** Another widget may be added only if we are below the limit. */
    fun canAdd(widgetIds: List<Int>): Boolean = widgetIds.size < MAX_WIDGETS

    /** Maximum allowed widget footprint, as a fraction of the usable Home area. */
    const val MAX_WIDTH_FRACTION = 0.90f
    const val MAX_HEIGHT_FRACTION = 0.10f

    fun maxAllowedWidth(availableWidthDp: Int): Int =
        (availableWidthDp * MAX_WIDTH_FRACTION).toInt().coerceAtLeast(0)

    fun maxAllowedHeight(availableHeightDp: Int): Int =
        (availableHeightDp * MAX_HEIGHT_FRACTION).toInt().coerceAtLeast(0)

    /**
     * A provider is acceptable only if its declared minimum size fits inside the
     * allowed AURA area (width and height both). Providers reporting 0 are treated as
     * acceptable (no constraint advertised); a 0 allowed area rejects everything.
     */
    fun isProviderAcceptable(
        providerMinWidthDp: Int,
        providerMinHeightDp: Int,
        maxWidthDp: Int,
        maxHeightDp: Int
    ): Boolean {
        if (maxWidthDp <= 0 || maxHeightDp <= 0) return false
        if (providerMinWidthDp <= 0 && providerMinHeightDp <= 0) return true
        return providerMinWidthDp <= maxWidthDp && providerMinHeightDp <= maxHeightDp
    }

    /** Clamp a (resizable) widget's desired size to AURA's maximum. Never expands. */
    fun clampSize(widthDp: Int, heightDp: Int, maxWidthDp: Int, maxHeightDp: Int): Pair<Int, Int> =
        widthDp.coerceAtMost(maxWidthDp) to heightDp.coerceAtMost(maxHeightDp)
}
