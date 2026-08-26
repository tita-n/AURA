package com.aura.home

import org.junit.Assert.*
import org.junit.Test

class HomeCodecsTest {

    @Test fun `dock round-trip empty`() {
        assertEquals("", HomeCodecs.encodeDock(emptyList()))
        assertEquals(emptyList<DockItem>(), HomeCodecs.decodeDock(""))
        assertEquals(emptyList<DockItem>(), HomeCodecs.decodeDock(null))
    }

    @Test fun `dock round-trip five`() {
        val dock = listOf(DockItem("a"), DockItem("b"), DockItem("c"), DockItem("d"), DockItem("e"))
        val enc = HomeCodecs.encodeDock(dock)
        assertEquals(dock, HomeCodecs.decodeDock(enc))
    }

    @Test fun `modules round-trip`() {
        val mods = listOf(HomeModuleType.NextEvent, HomeModuleType.Music)
        val enc = HomeCodecs.encodeModules(mods)
        assertEquals(mods, HomeCodecs.decodeModules(enc))
        assertEquals(emptyList<HomeModuleType>(), HomeCodecs.decodeModules(""))
        assertEquals(emptyList<HomeModuleType>(), HomeCodecs.decodeModules(null))
    }

    @Test fun `modules unknown token skipped`() {
        // Simulate migration where an old Weather token remains in prefs
        val decoded = HomeCodecs.decodeModules("NextEvent|Weather|Music")
        assertEquals(listOf(HomeModuleType.NextEvent, HomeModuleType.Music), decoded)
    }

    @Test fun `theme mode round-trip every variant`() {
        for (mode in ThemeMode.values()) {
            val enc = HomeCodecs.encodeThemeMode(mode)
            assertEquals(mode, HomeCodecs.decodeThemeMode(enc))
        }
        // Unknown falls back to System
        assertEquals(ThemeMode.System, HomeCodecs.decodeThemeMode("X"))
        assertEquals(ThemeMode.System, HomeCodecs.decodeThemeMode(null))
    }

    @Test fun `accent round-trip dynamic`() {
        val enc = HomeCodecs.encodeAccent(AccentChoice.Dynamic)
        assertEquals(AccentChoice.Dynamic, HomeCodecs.decodeAccent(enc))
    }

    @Test fun `accent round-trip curated`() {
        for (i in AccentPalette.entries.indices) {
            val enc = HomeCodecs.encodeAccent(AccentChoice.Curated(i))
            assertEquals(AccentChoice.Curated(i), HomeCodecs.decodeAccent(enc))
        }
    }

    @Test fun `accent invalid index falls back to dynamic`() {
        assertEquals(AccentChoice.Dynamic, HomeCodecs.decodeAccent("C:99"))
        assertEquals(AccentChoice.Dynamic, HomeCodecs.decodeAccent("C:-1"))
        assertEquals(AccentChoice.Dynamic, HomeCodecs.decodeAccent("C:abc"))
        assertEquals(AccentChoice.Dynamic, HomeCodecs.decodeAccent(null))
    }

    @Test fun `animation intensity round-trip`() {
        assertEquals("S", HomeCodecs.encodeAnimation(AnimationIntensity.Standard))
        assertEquals("R", HomeCodecs.encodeAnimation(AnimationIntensity.Reduced))
        assertEquals(AnimationIntensity.Standard, HomeCodecs.decodeAnimation("S"))
        assertEquals(AnimationIntensity.Reduced, HomeCodecs.decodeAnimation("R"))
        assertEquals(AnimationIntensity.Standard, HomeCodecs.decodeAnimation(null))
        assertEquals(AnimationIntensity.Standard, HomeCodecs.decodeAnimation("X"))
    }

    @Test fun `customization full map round-trip dark curated reduced with wallpaper`() {
        val custom = HomeCustomization(
            themeMode = ThemeMode.Dark,
            accent = AccentChoice.Curated(2),
            animationIntensity = AnimationIntensity.Reduced,
            showWallpaper = true
        )
        val map = HomeCodecs.encodeCustom(custom)
        val decoded = HomeCodecs.decodeCustom { k -> map[k] }
        assertEquals(custom, decoded)
    }

    @Test fun `customization light dynamic standard no wallpaper`() {
        val custom = HomeCustomization(
            themeMode = ThemeMode.Light,
            accent = AccentChoice.Dynamic,
            animationIntensity = AnimationIntensity.Standard,
            showWallpaper = false
        )
        val map = HomeCodecs.encodeCustom(custom)
        val decoded = HomeCodecs.decodeCustom { k -> map[k] }
        assertEquals(custom, decoded)
    }

    @Test fun `customization system fallback on missing keys`() {
        val decoded = HomeCodecs.decodeCustom { _ -> null }
        assertEquals(HomeCustomization(), decoded)
    }

    @Test fun `widget ids round-trip`() {
        val ids = listOf(11, 22, 33)
        assertEquals(ids, HomeCodecs.decodeWidgetIds(HomeCodecs.encodeWidgetIds(ids)))
        assertEquals(emptyList<Int>(), HomeCodecs.decodeWidgetIds(""))
        assertEquals(emptyList<Int>(), HomeCodecs.decodeWidgetIds(null))
        // garbage tokens skipped
        assertEquals(listOf(11, 33), HomeCodecs.decodeWidgetIds("11,,abc,33"))
    }

    @Test fun `pruneStoredWidgetIds keeps order of stored`() {
        val stored = listOf(5, 1, 9, 3)
        val live = listOf(1, 3, 5, 7)
        val pruned = HomeCodecs.pruneStoredWidgetIds(stored, live)
        assertEquals(listOf(5, 1, 3), pruned)
    }

    @Test fun `presence greeting contract`() {
        // Deterministic local-time greeting — always a value, keyed on the hour.
        assertEquals("Good night", Presence.greetingFor(0))
        assertEquals("Good night", Presence.greetingFor(3))
        assertEquals("Good morning", Presence.greetingFor(5))
        assertEquals("Good morning", Presence.greetingFor(8))
        assertEquals("Good morning", Presence.greetingFor(11))
        assertEquals("Good afternoon", Presence.greetingFor(12))
        assertEquals("Good afternoon", Presence.greetingFor(13))
        assertEquals("Good afternoon", Presence.greetingFor(16))
        assertEquals("Good evening", Presence.greetingFor(17))
        assertEquals("Good evening", Presence.greetingFor(19))
        assertEquals("Good evening", Presence.greetingFor(22))
        assertEquals("Good evening", Presence.greetingFor(23))
    }

    @Test fun `full HomeSettings survives encode then decode`() {
        val settings = HomeSettings(
            customization = HomeCustomization(ThemeMode.Dark, AccentChoice.Curated(1), AnimationIntensity.Reduced, true),
            dock = listOf(DockItem("com.a"), DockItem("com.b")),
            modules = listOf(HomeModuleType.Battery, HomeModuleType.Music),
            widgetIds = listOf(101, 102)
        )
        // Simulate prefs write/read via per-key codecs
        val dockRaw = HomeCodecs.encodeDock(settings.dock)
        val modsRaw = HomeCodecs.encodeModules(settings.modules)
        val widRaw = HomeCodecs.encodeWidgetIds(settings.widgetIds)
        val customMap = HomeCodecs.encodeCustom(settings.customization)
        val decoded = HomeSettings(
            customization = HomeCodecs.decodeCustom { k -> customMap[k] },
            dock = HomeCodecs.decodeDock(dockRaw),
            modules = HomeCodecs.decodeModules(modsRaw),
            widgetIds = HomeCodecs.decodeWidgetIds(widRaw)
        )
        assertEquals(settings, decoded)
    }
}
