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
import com.aura.platform.AndroidAppIndexProvider
import com.aura.resolver.IntentRouter
import com.aura.resolver.L0Index
import com.aura.resolver.L0IndexFactory
import com.aura.resolver.L0Resolver
import com.aura.resolver.l1.L1Resolver
import com.aura.resolver.l2.L2Resolver
import com.aura.ui.home.HomeScreen
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

                // Real Android app index — PackageManager off-main-thread, L0Index built once per dataset.
                // Flow: AndroidAppIndexProvider (IO) -> List<IndexedEntity> -> L0Index.build() -> L0Resolver/L1Resolver -> IntentRouter.
                // Contacts/settings remain from demo catalog; only apps are replaced with real launchable apps.
                val context = androidx.compose.ui.platform.LocalContext.current
                var currentIndex by remember { mutableStateOf(L0IndexFactory.demoIndex()) }
                var router by remember { mutableStateOf(IntentRouter(L0Resolver(currentIndex), L1Resolver(currentIndex), L2Resolver(currentIndex))) }

                // Explicit load off-main-thread — no polling, no WorkManager, no loops (Phase 1.5)
                LaunchedEffect(Unit) {
                    val realApps = withContext(Dispatchers.IO) {
                        try {
                            AndroidAppIndexProvider(context.applicationContext).getAppEntities()
                        } catch (_: Exception) { emptyList() }
                    }
                    if (realApps.isNotEmpty()) {
                        val newIndex = L0IndexFactory.buildIndex(realApps)
                        currentIndex = newIndex
                        router = IntentRouter(L0Resolver(newIndex), L1Resolver(newIndex), L2Resolver(newIndex))
                    }
                }

                // Real routing — re-evaluates when query or router (after real index load) changes
                LaunchedEffect(query, router) {
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
