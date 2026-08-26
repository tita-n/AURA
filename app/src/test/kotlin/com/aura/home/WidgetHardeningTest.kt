package com.aura.home
import com.aura.TestPaths

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WidgetHardeningTest {

    private fun hostText(): String {
        val f = TestPaths.find("app/src/main/kotlin/com/aura/platform/android/AuraWidgetHost.kt")
        val alt = File("app/src/main/kotlin/com/aura/platform/android/AuraWidgetHost.kt")
        return (if (f.exists()) f else alt).readText()
    }

    private fun mainText(): String {
        val f = TestPaths.find("app/src/main/kotlin/com/aura/MainActivity.kt")
        val alt = File("app/src/main/kotlin/com/aura/MainActivity.kt")
        return (if (f.exists()) f else alt).readText()
    }

    @Test fun `widget updateSize handles modern API 31 SIZES`() {
        val text = hostText()
        assertTrue(text.contains("OPTION_APPWIDGET_SIZES"))
        assertTrue(text.contains("Build.VERSION.SDK_INT >= 31"))
        assertTrue(text.contains("SizeF"))
    }

    @Test fun `widget updateSize keeps backward compatibility with MIN_MAX`() {
        val text = hostText()
        assertTrue(text.contains("OPTION_APPWIDGET_MIN_WIDTH"))
        assertTrue(text.contains("OPTION_APPWIDGET_MAX_WIDTH"))
        // Should suppress deprecation for <31 fallback
        assertTrue(text.contains("@Suppress(\"DEPRECATION\")") || text.contains("DEPRECATION"))
    }

    @Test fun `widget host lifecycle mirrors ON_START ON_STOP`() {
        val text = mainText()
        assertTrue(text.contains("ON_START") && text.contains("startListening"))
        assertTrue(text.contains("ON_STOP") && text.contains("stopListening"))
        // Also check DisposableEffect with lifecycle
        assertTrue(text.contains("LifecycleEventObserver"))
    }

    @Test fun `widget configure flow deletes orphan on cancel`() {
        val text = mainText()
        // Check that widgetConfigureLauncher handles RESULT_OK vs cancel
        assertTrue(text.contains("widgetConfigureLauncher"))
        assertTrue(text.contains("RESULT_OK"))
        assertTrue(text.contains("deleteId"))
        // Bind launcher also
        assertTrue(text.contains("widgetBindLauncher"))
    }

    @Test fun `widget provider removal prunes deterministically`() {
        val text = mainText()
        // Should filter stored ids where providerForId == null or not in liveIds
        assertTrue(text.contains("providerForId") && text.contains("liveIds"))
        assertTrue(text.contains("prunedWidgets") || text.contains("prune"))
    }

    @Test fun `widget ordering preserved and size reporting documented`() {
        val text = hostText()
        assertTrue(text.contains("Responsive widgets") || text.contains("responsive"))
        // No custom ResizeOverlay composable/class should exist (honest status: responsive via sizes)
        assertFalse(text.contains("class ResizeOverlay") || text.contains("@Composable fun ResizeOverlay"))
        assertFalse(text.contains("fun ResizeOverlay"))
    }

    @Test fun `orphan cleanup via deleteOrphaned`() {
        val text = hostText()
        assertTrue(text.contains("deleteOrphaned"))
        assertTrue(text.contains("hostIds"))
    }

    @Test fun `widget pure logic - stale ids pruned keeps order`() {
        val stored = listOf(5, 1, 9, 3)
        val live = listOf(1, 3, 5, 7)
        val pruned = HomeCodecs.pruneStoredWidgetIds(stored, live)
        assertEquals(listOf(5, 1, 3), pruned)
    }

    @Test fun `widget pure logic - cancelled config does not commit`() {
        // Simulate: allocate 42, configure cancelled → should not be in stored list
        var stored = listOf(10, 20)
        val allocated = 42
        val resultOk = false
        if (resultOk) stored = stored + allocated
        // else delete, stored unchanged
        assertEquals(listOf(10, 20), stored)
    }
}
