package com.aura.home

import org.junit.Assert.*
import org.junit.Test

class ModuleLogicTest {

    @Test fun `enable adds at canonical position`() {
        val mods = ModuleLogic.enable(emptyList(), HomeModuleType.Music)
        assertEquals(listOf(HomeModuleType.Music), mods)
        val withBattery = ModuleLogic.enable(mods, HomeModuleType.Battery)
        // Battery canonical before Music, so Battery should be inserted before Music
        assertEquals(listOf(HomeModuleType.Battery, HomeModuleType.Music), withBattery)
    }

    @Test fun `enable already enabled is no-op identity`() {
        val mods = listOf(HomeModuleType.Battery)
        val same = ModuleLogic.enable(mods, HomeModuleType.Battery)
        assertSame(mods, same)
    }

    @Test fun `disable removes`() {
        val mods = listOf(HomeModuleType.NextEvent, HomeModuleType.Battery)
        val next = ModuleLogic.disable(mods, HomeModuleType.NextEvent)
        assertEquals(listOf(HomeModuleType.Battery), next)
    }

    @Test fun `disable absent is no-op`() {
        val mods = listOf(HomeModuleType.Battery)
        val same = ModuleLogic.disable(mods, HomeModuleType.Music)
        assertSame(mods, same)
    }

    @Test fun `shift up and down`() {
        val mods = listOf(HomeModuleType.NextEvent, HomeModuleType.Battery, HomeModuleType.Music)
        val shiftedDown = ModuleLogic.shift(mods, HomeModuleType.NextEvent, +1)
        assertEquals(listOf(HomeModuleType.Battery, HomeModuleType.NextEvent, HomeModuleType.Music), shiftedDown)
        val shiftedUp = ModuleLogic.shift(mods, HomeModuleType.Music, -1)
        assertEquals(listOf(HomeModuleType.NextEvent, HomeModuleType.Music, HomeModuleType.Battery), shiftedUp)
    }

    @Test fun `shift out of bounds no-op`() {
        val mods = listOf(HomeModuleType.Battery, HomeModuleType.Music)
        assertSame(mods, ModuleLogic.shift(mods, HomeModuleType.Battery, -1))
        assertSame(mods, ModuleLogic.shift(mods, HomeModuleType.Music, +1))
        assertSame(mods, ModuleLogic.shift(mods, HomeModuleType.NextEvent, +1))
    }

    @Test fun `ordering round-trip via enable disable`() {
        var mods: List<HomeModuleType> = emptyList()
        mods = ModuleLogic.enable(mods, HomeModuleType.Battery)
        mods = ModuleLogic.enable(mods, HomeModuleType.NextEvent)
        mods = ModuleLogic.enable(mods, HomeModuleType.Music)
        // Canonical order enforced: NextEvent, Battery, Music
        assertEquals(listOf(HomeModuleType.NextEvent, HomeModuleType.Battery, HomeModuleType.Music), mods)
        mods = ModuleLogic.disable(mods, HomeModuleType.Battery)
        assertEquals(listOf(HomeModuleType.NextEvent, HomeModuleType.Music), mods)
        mods = ModuleLogic.enable(mods, HomeModuleType.Battery)
        assertEquals(listOf(HomeModuleType.NextEvent, HomeModuleType.Battery, HomeModuleType.Music), mods)
    }

    @Test fun `empty-data behavior — empty modules is valid`() {
        val mods: List<HomeModuleType> = emptyList()
        assertFalse(ModuleLogic.isEnabled(mods, HomeModuleType.Battery))
        assertEquals(emptyList<HomeModuleType>(), ModuleLogic.disable(mods, HomeModuleType.Music))
    }

    @Test fun `isEnabled reflects membership`() {
        val mods = listOf(HomeModuleType.Music)
        assertTrue(ModuleLogic.isEnabled(mods, HomeModuleType.Music))
        assertFalse(ModuleLogic.isEnabled(mods, HomeModuleType.Battery))
    }
}
