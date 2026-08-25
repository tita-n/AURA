package com.aura.home

/**
 * Pure module ordering/enablement logic — no Compose, no Android APIs.
 * [HomeSettings.modules] holds ENABLED modules only, already in display order.
 */
object ModuleLogic {

    /** Canonical order used for stable re-enable insertion. */
    val CANONICAL_ORDER = listOf(HomeModuleType.NextEvent, HomeModuleType.Battery, HomeModuleType.Music)

    fun enable(modules: List<HomeModuleType>, module: HomeModuleType): List<HomeModuleType> {
        if (module in modules) return modules
        // Insert at canonical position so re-enabling never scrambles user order.
        val candidates = CANONICAL_ORDER.indexOf(module)
        var insertAt = modules.size
        for (i in modules.indices) {
            if (CANONICAL_ORDER.indexOf(modules[i]) > candidates) {
                insertAt = i
                break
            }
        }
        return modules.subList(0, insertAt) + module + modules.subList(insertAt, modules.size)
    }

    fun disable(modules: List<HomeModuleType>, module: HomeModuleType): List<HomeModuleType> =
        if (module !in modules) modules else modules - module

    /** Shift a module one position up (-1) or down (+1). Bounds-safe. */
    fun shift(modules: List<HomeModuleType>, module: HomeModuleType, delta: Int): List<HomeModuleType> {
        val index = modules.indexOf(module)
        if (index < 0) return modules
        val target = index + delta
        if (target !in modules.indices) return modules
        val mutable = modules.toMutableList()
        mutable[index] = mutable[target]
        mutable[target] = module
        return mutable
    }

    fun isEnabled(modules: List<HomeModuleType>, module: HomeModuleType): Boolean = module in modules
}
