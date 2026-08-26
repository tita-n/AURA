package com.aura.library

import com.aura.ui.library.AppLibraryLogic
import com.aura.ui.library.AppLibraryRail
import org.junit.Assert.*
import org.junit.Test

/**
 * The A-Z rail must stay genuinely coupled to the list scroll position (not faked by the
 * tap), and missing letters must resolve to the nearest available section. All math is
 * pure and tested here, independent of LazyListState.
 */
class AppLibraryRailTest {

    private fun sec(letter: String, idx: Int) = AppLibraryLogic.Section(letter, idx)

    @Test fun `header offset accounts for the search field`() {
        assertEquals(1, AppLibraryRail.HEADER_OFFSET)
    }

    @Test fun `active section tracks the scroll index`() {
        val sections = listOf(sec("A", 0), sec("B", 2), sec("C", 4))
        assertEquals(0, AppLibraryRail.activeSectionIndex(sections, 0))
        assertEquals(0, AppLibraryRail.activeSectionIndex(sections, 1))
        assertEquals(1, AppLibraryRail.activeSectionIndex(sections, 2))
        assertEquals(1, AppLibraryRail.activeSectionIndex(sections, 3))
        assertEquals(2, AppLibraryRail.activeSectionIndex(sections, 4))
        assertEquals(2, AppLibraryRail.activeSectionIndex(sections, 5))
    }

    @Test fun `empty sections yields a stable zero index`() {
        assertEquals(0, AppLibraryRail.activeSectionIndex(emptyList(), 0))
    }

    @Test fun `list scroll index offsets for the header`() {
        assertEquals(AppLibraryRail.HEADER_OFFSET, AppLibraryRail.listScrollIndex(0))
        assertEquals(3 + AppLibraryRail.HEADER_OFFSET, AppLibraryRail.listScrollIndex(3))
    }

    @Test fun `exact letter maps to its start index`() {
        val sections = listOf(sec("A", 0), sec("B", 1), sec("C", 3), sec("E", 6))
        assertEquals(0, AppLibraryRail.targetIndexForLetter(sections, "A"))
        assertEquals(1, AppLibraryRail.targetIndexForLetter(sections, "B"))
        assertEquals(3, AppLibraryRail.targetIndexForLetter(sections, "C"))
        assertEquals(6, AppLibraryRail.targetIndexForLetter(sections, "E"))
    }

    @Test fun `missing letter resolves to nearest available section`() {
        val sections = listOf(sec("A", 0), sec("B", 1), sec("C", 3), sec("E", 6))
        // D sits between C and E -> nearest is C (start 3)
        assertEquals(3, AppLibraryRail.targetIndexForLetter(sections, "D"))
        // F is after E -> nearest E (start 6)
        assertEquals(6, AppLibraryRail.targetIndexForLetter(sections, "F"))
        // '#' is before A -> nearest A (start 0)
        assertEquals(0, AppLibraryRail.targetIndexForLetter(sections, "#"))
    }

    @Test fun `empty sections have no target`() {
        assertNull(AppLibraryRail.targetIndexForLetter(emptyList(), "A"))
    }
}
