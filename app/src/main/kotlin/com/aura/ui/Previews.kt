package com.aura.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aura.PreviewData
import com.aura.design.AuraTheme
import com.aura.domain.CommandState
import com.aura.ui.command.CommandStateHost
import com.aura.ui.components.*
import com.aura.ui.home.HomeScreen

// ----- Theme previews -----

@Preview(name = "Home — Dark — Idle", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewHomeDarkIdle() {
    AuraTheme(darkTheme = true) {
        Box(modifier = Modifier.background(AuraTheme.colors.surfaceBase)) {
            HomeScreen(commandState = CommandState.Idle, presenceText = "Good morning")
        }
    }
}

@Preview(name = "Home — Light — Idle", showBackground = true, backgroundColor = 0xFFFAF9F6)
@Composable
fun PreviewHomeLightIdle() {
    AuraTheme(darkTheme = false) {
        Box(modifier = Modifier.background(AuraTheme.colors.surfaceBase)) {
            HomeScreen(commandState = CommandState.Idle, presenceText = "Good morning")
        }
    }
}

@Preview(name = "Home — Dark — ACT", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewHomeAct() {
    AuraTheme(darkTheme = true) {
        Box(modifier = Modifier.background(AuraTheme.colors.surfaceBase)) {
            HomeScreen(
                commandState = CommandState.Act(PreviewData.actContactWithChips),
                query = "message sarah",
                focused = true,
                presenceText = null
            )
        }
    }
}

@Preview(name = "Home — ASK Which Sarah", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewHomeAsk() {
    AuraTheme(darkTheme = true) {
        Box(modifier = Modifier.background(AuraTheme.colors.surfaceBase)) {
            HomeScreen(
                commandState = CommandState.Ask(PreviewData.askWhichSarah),
                query = "sarah",
                focused = true
            )
        }
    }
}

@Preview(name = "Home — Empty", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewHomeEmpty() {
    AuraTheme(darkTheme = true) {
        Box(modifier = Modifier.background(AuraTheme.colors.surfaceBase)) {
            HomeScreen(commandState = CommandState.Empty("zxq"), query = "zxq", focused = true)
        }
    }
}

@Preview(name = "Home — Error", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewHomeError() {
    AuraTheme(darkTheme = true) {
        Box(modifier = Modifier.background(AuraTheme.colors.surfaceBase)) {
            HomeScreen(commandState = CommandState.Error(PreviewData.errorExample), query = "error", focused = true)
        }
    }
}

@Preview(name = "InlineResult — Math", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewInlineMath() {
    AuraTheme(darkTheme = true) {
        Column(modifier = Modifier.background(AuraTheme.colors.surfaceBase).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InlineResult(value = "13,500", query = "500 * 27", onCopy = {})
            InlineConfirmation(phrase = "Alarm set for 6:30", onUndo = {})
        }
    }
}

@Preview(name = "StatusIndicator", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewStatus() {
    AuraTheme(darkTheme = true) {
        Box(modifier = Modifier.background(AuraTheme.colors.surfaceBase).padding(16.dp)) {
            StatusIndicator()
        }
    }
}

@Preview(name = "Chips", showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
fun PreviewChips() {
    AuraTheme(darkTheme = true) {
        Row(modifier = Modifier.background(AuraTheme.colors.surfaceBase).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuraChip(variant = ChipVariant.Action("Message"), onClick = {})
            AuraChip(variant = ChipVariant.Undo("Undo", onUndo = {}), onClick = {})
            AuraChip(variant = ChipVariant.Secondary("Copy"), onClick = {})
        }
    }
}

// Accessibility: large font scaling preview (200%)
@Preview(name = "Home — Large Font", showBackground = true, backgroundColor = 0xFF0A0A0B, fontScale = 2f)
@Composable
fun PreviewHomeLargeFont() {
    AuraTheme(darkTheme = true) {
        Box(modifier = Modifier.background(AuraTheme.colors.surfaceBase)) {
            HomeScreen(commandState = CommandState.Act(PreviewData.actContactWithChips), query = "message sarah", focused = true)
        }
    }
}
