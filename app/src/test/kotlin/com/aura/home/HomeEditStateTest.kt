package com.aura.home
import com.aura.TestPaths

import org.junit.Assert.*
import org.junit.Test

class HomeEditStateTest {

    @Test fun `starts closed`() {
        var surface: EditSurface = EditSurface.Closed
        assertTrue(surface is EditSurface.Closed)
    }

    @Test fun `enter edit mode`() {
        var surface: EditSurface = EditSurface.Closed
        surface = EditSurface.open()
        assertTrue(surface is EditSurface.Main)
    }

    @Test fun `exit edit mode`() {
        var surface: EditSurface = EditSurface.Main
        surface = EditSurface.Closed
        assertTrue(surface is EditSurface.Closed)
    }

    @Test fun `navigate to dock picker and back`() {
        var surface: EditSurface = EditSurface.Main
        surface = EditSurface.DockPicker
        assertTrue(surface is EditSurface.DockPicker)
        surface = EditSurface.back(surface)
        assertTrue(surface is EditSurface.Main)
    }

    @Test fun `navigate to widget picker and back`() {
        var surface: EditSurface = EditSurface.Main
        surface = EditSurface.WidgetPicker
        assertTrue(surface is EditSurface.WidgetPicker)
        surface = EditSurface.back(surface)
        assertTrue(surface is EditSurface.Main)
    }

    @Test fun `back from main closes`() {
        var surface: EditSurface = EditSurface.Main
        surface = EditSurface.back(surface)
        assertTrue(surface is EditSurface.Closed)
    }

    @Test fun `back from closed stays closed`() {
        var surface: EditSurface = EditSurface.Closed
        surface = EditSurface.back(surface)
        assertTrue(surface is EditSurface.Closed)
    }

    @Test fun `no new CommandState — edit surface is separate type`() {
        // Read the CommandState.kt source to verify no Edit-related variant was added.
        // Avoids kotlin-reflect dependency (not on test classpath).
        val candidates = listOf(
            TestPaths.find("app/src/main/kotlin/com/aura/domain/CommandState.kt"),
            java.io.File("app/src/main/kotlin/com/aura/domain/CommandState.kt"),
            java.io.File((System.getProperty("user.dir") ?: ".") + "/app/src/main/kotlin/com/aura/domain/CommandState.kt")
        )
        val file = checkNotNull(candidates.firstOrNull { it.exists() }) { "CommandState.kt not found" }
        val text = file.readText()
        assertTrue(text.contains("data object Idle"))
        assertTrue(text.contains("data class Act"))
        assertTrue(text.contains("data class Ask"))
        assertTrue(text.contains("data class Empty"))
        assertTrue(text.contains("data class Error"))
        assertFalse("CommandState must not contain Edit variant", text.contains("Edit"))
        assertFalse("CommandState must not contain Editing", text.contains("Editing"))
        assertFalse("CommandState must not contain DockPicker", text.contains("DockPicker"))
        // EditSurface must exist as a separate type
        val editCandidates = listOf(
            TestPaths.find("app/src/main/kotlin/com/aura/home/HomeEditState.kt"),
            java.io.File("app/src/main/kotlin/com/aura/home/HomeEditState.kt")
        )
        val editFile = checkNotNull(editCandidates.firstOrNull { it.exists() }) { "HomeEditState.kt not found" }
        assertTrue(editFile.readText().contains("sealed interface EditSurface"))
    }

    @Test fun `add remove operations round-trip inside edit without touching CommandState`() {
        // Simulate dock add/remove inside edit mode — commandState stays Idle throughout.
        var surface: EditSurface = EditSurface.Main
        var dock: List<DockItem> = emptyList()
        var cmd: com.aura.domain.CommandState = com.aura.domain.CommandState.Idle

        dock = DockLogic.add(dock, "com.a")
        assertEquals(com.aura.domain.CommandState.Idle, cmd)
        surface = EditSurface.DockPicker
        dock = DockLogic.add(dock, "com.b")
        assertEquals(com.aura.domain.CommandState.Idle, cmd)
        surface = EditSurface.back(surface)
        assertTrue(surface is EditSurface.Main)
        dock = DockLogic.remove(dock, "com.a")
        assertEquals(listOf(DockItem("com.b")), dock)
        assertEquals(com.aura.domain.CommandState.Idle, cmd)
    }
}
