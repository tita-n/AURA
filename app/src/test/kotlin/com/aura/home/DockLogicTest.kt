package com.aura.home

import org.junit.Assert.*
import org.junit.Test

class DockLogicTest {

    @Test fun `dock starts empty`() {
        assertTrue(DockLogic.prune(emptyList(), emptySet()).isEmpty())
    }

    @Test fun `add single item`() {
        val dock = DockLogic.add(emptyList(), "com.example.a")
        assertEquals(1, dock.size)
        assertEquals("com.example.a", dock[0].packageName)
    }

    @Test fun `dock honors MAX of four`() {
        assertEquals(4, DockLogic.MAX)
        var dock: List<DockItem> = emptyList()
        repeat(4) { dock = DockLogic.add(dock, "com.p$it") }
        assertEquals(4, dock.size)
        // fifth is rejected
        val overflow = DockLogic.add(dock, "com.overflow")
        assertEquals(4, overflow.size)
        assertSame(dock, overflow)
    }

    @Test fun `duplicate rejected`() {
        val dock = DockLogic.add(emptyList(), "com.a")
        val dup = DockLogic.add(dock, "com.a")
        assertSame(dock, dup)
        assertEquals(1, dup.size)
    }

    @Test fun `remove existing`() {
        val dock = DockLogic.add(DockLogic.add(emptyList(), "com.a"), "com.b")
        val removed = DockLogic.remove(dock, "com.a")
        assertEquals(listOf(DockItem("com.b")), removed)
    }

    @Test fun `remove unknown is no-op identity`() {
        val dock = DockLogic.add(emptyList(), "com.a")
        val same = DockLogic.remove(dock, "com.unknown")
        assertSame(dock, same)
    }

    @Test fun `reorder move`() {
        val dock = listOf(DockItem("a"), DockItem("b"), DockItem("c"))
        val moved = DockLogic.move(dock, 0, 2)
        assertEquals(listOf(DockItem("b"), DockItem("c"), DockItem("a")), moved)
    }

    @Test fun `reorder out of bounds no-op`() {
        val dock = listOf(DockItem("a"), DockItem("b"))
        assertSame(dock, DockLogic.move(dock, 5, 0))
        assertSame(dock, DockLogic.move(dock, 0, 5))
        assertSame(dock, DockLogic.move(dock, 0, 0))
    }

    @Test fun `prune removes uninstalled`() {
        val dock = listOf(DockItem("com.keep"), DockItem("com.gone"))
        val pruned = DockLogic.prune(dock, setOf("com.keep", "com.other"))
        assertEquals(listOf(DockItem("com.keep")), pruned)
    }

    @Test fun `prune empty installed clears all`() {
        val dock = listOf(DockItem("a"), DockItem("b"))
        assertTrue(DockLogic.prune(dock, emptySet()).isEmpty())
    }

    @Test fun `blank package ignored`() {
        val dock = DockLogic.add(emptyList(), "")
        assertTrue(dock.isEmpty())
        val dock2 = DockLogic.add(emptyList(), "   ")
        assertTrue(dock2.isEmpty())
    }
}
