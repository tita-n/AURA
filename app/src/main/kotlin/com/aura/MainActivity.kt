package com.aura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.design.AuraTheme
import com.aura.domain.*
import com.aura.ui.home.HomeScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Dark is default per spec, but respect system for accessibility verification
            AuraTheme(darkTheme = isSystemInDarkTheme()) {
                var query by remember { mutableStateOf("") }
                var commandState: CommandState by remember { mutableStateOf(CommandState.Idle) }
                var focused by remember { mutableStateOf(false) }
                var isResolving by remember { mutableStateOf(false) }

                // Demo shell logic — maps query to fake ACT/ASK to showcase patterns
                // No resolver layers are invoked; this is purely UI shell demonstration.
                // The mapping is deterministic and does not expose any layer identity.
                LaunchedEffect(query) {
                    commandState = when {
                        query.isBlank() -> CommandState.Idle
                        query.equals("error", ignoreCase = true) -> CommandState.Error(PreviewData.errorExample)
                        query.equals("empty", ignoreCase = true) -> CommandState.Empty(query)
                        query.contains("sarah", ignoreCase = true) && query.contains("which", ignoreCase = true) ->
                            CommandState.Ask(PreviewData.askWhichSarah)
                        query.contains("sarah", ignoreCase = true) -> CommandState.Act(PreviewData.actContactWithChips)
                        query.matches(Regex(".*\\d+\\s*[\\*x]\\s*\\d+.*")) -> CommandState.Act(PreviewData.actMath)
                        query.contains("alarm", ignoreCase = true) -> CommandState.Act(PreviewData.actAlarm)
                        query.contains("chrome", ignoreCase = true) -> CommandState.Act(PreviewData.actApp)
                        query == "ask" -> CommandState.Ask(PreviewData.askChooseAction)
                        query == "related" -> CommandState.Ask(PreviewData.askRelated)
                        query == "resolving" -> {
                            isResolving = true
                            CommandState.Idle
                        }
                        else -> CommandState.Empty(query)
                    }
                    if (query != "resolving") isResolving = false
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AuraTheme.colors.surfaceBase)
                ) {
                    HomeScreen(
                        commandState = commandState,
                        isResolving = isResolving,
                        query = query,
                        focused = focused,
                        onQueryChange = { query = it },
                        onFocusedChange = { focused = it },
                        presenceText = when {
                            query.isNotBlank() -> null
                            else -> "Good morning" // deterministic Presence, silence valid when typing
                        },
                        onActExecute = { /* shell: no execution */ },
                        onCandidateSelect = { candidate ->
                            // Collapse ASK → ACT (that transition IS confirmation, DL §15)
                            commandState = CommandState.Act(
                                ResolvedResult(
                                    id = candidate.id,
                                    title = candidate.title,
                                    subtitle = candidate.disambiguation,
                                    type = ResultType.Contact,
                                    action = AuraAction.NoOp
                                )
                            )
                        },
                        onActionChipClick = { },
                        onCopy = {}
                    )
                }
            }
        }
    }
}
