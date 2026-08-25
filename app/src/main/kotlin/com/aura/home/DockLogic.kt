package com.aura.home

/**
 * Pure dock operations — no Compose, no Android APIs, fully unit-testable.
 * Rules: 0–5 items, no duplicate packages, prune on uninstall.
 */
object DockLogic {

    const val MAX = 5

    /** Add a package. Duplicate-safe; silently no-ops at capacity. Returns the same list instance when unchanged. */
    fun add(dock: List<DockItem>, packageName: String): List<DockItem> {
        if (packageName.isBlank()) return dock
        if (dock.any { it.packageName == packageName }) return dock
        if (dock.size >= MAX) return dock
        return dock + DockItem(packageName)
    }

    fun remove(dock: List<DockItem>, packageName: String): List<DockItem> =
        if (dock.none { it.packageName == packageName }) dock
        else dock.filterNot { it.packageName == packageName }

    /** Move item currently at [fromIndex] to [toIndex]. Bounds-safe, identity-preserving otherwise. */
    fun move(dock: List<DockItem>, fromIndex: Int, toIndex: Int): List<DockItem> {
        if (fromIndex !in dock.indices || toIndex !in dock.indices || fromIndex == toIndex) return dock
        val mutable = dock.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }

    /**
     * Drop entries whose package is no longer installed.
     * Called after index refresh (install/uninstall events) and on load.
     */
    fun prune(dock: List<DockItem>, installedPackages: Set<String>): List<DockItem> =
        dock.filter { it.packageName in installedPackages }
}
