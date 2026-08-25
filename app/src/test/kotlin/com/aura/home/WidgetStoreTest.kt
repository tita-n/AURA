package com.aura.home

import org.junit.Assert.*
import org.junit.Test

class WidgetStoreTest {

    @Test fun `prune removes stale ids`() {
        val stored = listOf(10, 20, 30)
        val live = listOf(10, 30, 40)
        val pruned = HomeCodecs.pruneStoredWidgetIds(stored, live)
        assertEquals(listOf(10, 30), pruned)
    }

    @Test fun `prune empty stored`() {
        assertTrue(HomeCodecs.pruneStoredWidgetIds(emptyList(), listOf(1, 2)).isEmpty())
    }

    @Test fun `prune empty live clears`() {
        assertTrue(HomeCodecs.pruneStoredWidgetIds(listOf(1, 2), emptyList()).isEmpty())
    }

    @Test fun `prune preserves stored order not live order`() {
        val stored = listOf(3, 1, 2)
        val live = listOf(1, 2, 3)
        assertEquals(listOf(3, 1, 2), HomeCodecs.pruneStoredWidgetIds(stored, live))
    }

    @Test fun `widget ids encode decode round-trip`() {
        val ids = listOf(42, 7, 100)
        assertEquals(ids, HomeCodecs.decodeWidgetIds(HomeCodecs.encodeWidgetIds(ids)))
    }

    @Test fun `reorder widget ids via move semantics`() {
        var ids = listOf(10, 20, 30)
        // move 30 before 20
        ids = ids.toMutableList().also { val v = it.removeAt(2); it.add(1, v) }
        assertEquals(listOf(10, 30, 20), ids)
        // move 10 to end
        ids = ids.toMutableList().also { val v = it.removeAt(0); it.add(2, v) }
        assertEquals(listOf(30, 20, 10), ids)
    }

    @Test fun `invalid provider is expressed as prune`() {
        // Simulate app uninstall: widget id 99's provider gone from live host set
        val stored = listOf(10, 99, 20)
        val live = listOf(10, 20) // 99 missing
        val pruned = HomeCodecs.pruneStoredWidgetIds(stored, live)
        assertEquals(listOf(10, 20), pruned)
        assertFalse(pruned.contains(99))
    }

    @Test fun `add widget id preserves five-free ordering`() {
        var ids: List<Int> = emptyList()
        ids = ids + 101
        ids = ids + 102
        assertEquals(listOf(101, 102), ids)
        // remove first
        ids = ids.filterNot { it == 101 }
        assertEquals(listOf(102), ids)
    }
}
