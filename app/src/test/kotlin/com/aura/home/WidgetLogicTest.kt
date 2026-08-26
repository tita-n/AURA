package com.aura.home

import org.junit.Assert.*
import org.junit.Test

/**
 * AURA allows exactly ONE third-party widget, and it must not dominate Home.
 * All decisions are pure and unit-tested so the launcher never silently grows a
 * widget wall or stretches a widget across the screen.
 */
class WidgetLogicTest {

    @Test fun `AURA allows exactly one widget`() {
        assertEquals(1, WidgetLogic.MAX_WIDGETS)
    }

    @Test fun `canAdd respects the one-widget limit`() {
        assertTrue(WidgetLogic.canAdd(emptyList()))
        assertFalse(WidgetLogic.canAdd(listOf(1)))
        assertFalse(WidgetLogic.canAdd(listOf(1, 2)))
    }

    @Test fun `max allowed width is 90 percent of usable area`() {
        assertEquals(360, WidgetLogic.maxAllowedWidth(400))
        assertEquals(0, WidgetLogic.maxAllowedWidth(0))
        assertEquals(90, WidgetLogic.maxAllowedWidth(100))
    }

    @Test fun `max allowed height is 10 percent of usable area`() {
        assertEquals(80, WidgetLogic.maxAllowedHeight(800))
        assertEquals(0, WidgetLogic.maxAllowedHeight(0))
        assertEquals(10, WidgetLogic.maxAllowedHeight(100))
    }

    @Test fun `provider is acceptable when it fits the allowed area`() {
        assertTrue(WidgetLogic.isProviderAcceptable(100, 50, 360, 80))
    }

    @Test fun `provider is rejected when too wide`() {
        assertFalse(WidgetLogic.isProviderAcceptable(400, 50, 360, 80))
    }

    @Test fun `provider is rejected when too tall`() {
        assertFalse(WidgetLogic.isProviderAcceptable(100, 90, 360, 80))
    }

    @Test fun `a zero allowed area rejects everything`() {
        assertFalse(WidgetLogic.isProviderAcceptable(10, 10, 0, 80))
        assertFalse(WidgetLogic.isProviderAcceptable(10, 10, 360, 0))
    }

    @Test fun `providers advertising no minimum size are acceptable`() {
        assertTrue(WidgetLogic.isProviderAcceptable(0, 0, 360, 80))
    }

    @Test fun `partially-zero dimensions are still checked`() {
        assertTrue(WidgetLogic.isProviderAcceptable(100, 0, 360, 80))  // width fits
        assertFalse(WidgetLogic.isProviderAcceptable(400, 0, 360, 80)) // width exceeds
    }

    @Test fun `clamp never expands a widget`() {
        assertEquals(200 to 50, WidgetLogic.clampSize(200, 50, 360, 80))
        assertEquals(360 to 50, WidgetLogic.clampSize(500, 50, 360, 80))
        assertEquals(200 to 80, WidgetLogic.clampSize(200, 200, 360, 80))
        assertEquals(0 to 0, WidgetLogic.clampSize(0, 0, 360, 80))
    }
}
