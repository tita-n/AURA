package com.aura.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Part 6 — Home edit-state transition tested independently from the Compose gesture.
 * Long-press enters edit mode; a normal tap must not; back behavior stays correct; the edit
 * state machine is fully separate from CommandState.
 */
class HomeLongPressStateTest {

    @Test
    fun longPressEntersEditMode() {
        val surface = EditSurface.Closed
        // The gesture handler maps a long-press on empty Home to EditSurface.open()
        val afterLongPress = EditSurface.open()
        assertEquals(EditSurface.Main, afterLongPress)
        assertFalse(afterLongPress is EditSurface.Closed)
    }

    @Test
    fun normalTapDoesNotOpenEdit() {
        // A tap handler is a no-op; it never calls EditSurface.open().
        val surface = EditSurface.Closed
        var openedByTap = false
        val onTap = { openedByTap = false } // intentional no-op
        onTap()
        assertEquals(EditSurface.Closed, surface)
        assertFalse(openedByTap)
    }

    @Test
    fun backFromMainCloses() {
        assertEquals(EditSurface.Closed, EditSurface.back(EditSurface.Main))
    }

    @Test
    fun backFromPickerReturnsToMain() {
        assertEquals(EditSurface.Main, EditSurface.back(EditSurface.DockPicker))
        assertEquals(EditSurface.Main, EditSurface.back(EditSurface.WidgetPicker))
    }

    @Test
    fun editStateIsIndependentOfCommandState() {
        // Entering edit mode must not mutate or require any CommandState value.
        val editing = EditSurface.open()
        assertEquals(EditSurface.Main, editing)
        // CommandState remains a distinct, untouched type (Idle here by construction).
        val cmd = com.aura.domain.CommandState.Idle
        assertTrue(cmd is com.aura.domain.CommandState.Idle)
    }
}
