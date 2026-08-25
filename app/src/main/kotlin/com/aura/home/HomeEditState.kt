package com.aura.home

/**
 * Edit-mode surface state machine — pure, no Compose.
 * The editing surface is deliberately small: main sheet plus two pickers.
 * This state is fully separate from CommandState; entering/exiting edit mode
 * never creates or mutates any CommandState value.
 */
sealed interface EditSurface {
    /** No editing surface shown. */
    data object Closed : EditSurface

    /** Main edit sheet: modules, widgets, appearance, dock. */
    data object Main : EditSurface

    /** Picker for adding an installed app to the dock. Back returns to [Main]. */
    data object DockPicker : EditSurface

    /** Picker for adding an Android widget. Back returns to [Main]. */
    data object WidgetPicker : EditSurface

    companion object {
        fun open(): EditSurface = Main

        fun back(surface: EditSurface): EditSurface = when (surface) {
            DockPicker, WidgetPicker -> Main
            else -> Closed
        }
    }
}
