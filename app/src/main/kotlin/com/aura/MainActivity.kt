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
import com.aura.resolver.IntentRouter
import com.aura.resolver.L0Index
import com.aura.resolver.L0IndexFactory
import com.aura.resolver.L0Resolver
import com.aura.resolver.l1.L1Resolver
import com.aura.ui.home.HomeScreen
import androidx.compose.foundation.isSystemInDarkTheme

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

                // L0 + L1 — built once, queried cheaply (L0 <10ms, L1 <5ms) on every keystroke.
                // Index is in-memory for v0.1; platform provider will replace demo data via WorkManager later.
                val index = remember { L0IndexFactory.demoIndex() }
                val router = remember { IntentRouter(L0Resolver(index), L1Resolver(index)) }

                // Real L0 routing — produces only Act/Ask/Idle/Empty/Error, no provenance.
                LaunchedEffect(query) {
                    if (query == "resolving") {
                        isResolving = true
                        commandState = CommandState.Idle
                        return@LaunchedEffect
                    }
                    isResolving = false
                    commandState = when {
                        query.isBlank() -> CommandState.Idle
                        // Keep explicit error demo for preview — L0 itself never returns Error for no-match
                        query.equals("error", ignoreCase = true) -> CommandState.Error(PreviewData.errorExample)
                        else -> router.routeToCommandState(query)
                    }
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
