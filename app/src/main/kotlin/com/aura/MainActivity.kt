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
import com.aura.resolver.l3.L3Validator
import com.aura.ui.home.HomeScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.rememberCoroutineScope
import com.aura.platform.android.AndroidActionExecutor
import com.aura.platform.android.ExecutionResult
import com.aura.resolver.l3.ValidatedAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                val scope = rememberCoroutineScope()
                val executor = remember(context) { AndroidActionExecutor(context.applicationContext) }
                val currentIndexState = remember { mutableStateOf(L0IndexFactory.demoIndex()) }
                val routerState = remember {
                    val idx = currentIndexState.value
                    mutableStateOf(IntentRouter(L0Resolver(idx), L1Resolver(idx), L2Resolver(idx), L3Validator(idx)))
                }

                // Explicit load off-main-thread — no polling, no WorkManager, no loops (Phase 1.5)
                LaunchedEffect(Unit) {
                    val realApps = withContext(Dispatchers.IO) {
                        try {
                            AndroidAppIndexProvider(context.applicationContext).getAppEntities()
                        } catch (_: Exception) { emptyList() }
                    }
                    if (realApps.isNotEmpty()) {
                        val newIndex = L0IndexFactory.buildIndex(realApps)
                        currentIndexState.value = newIndex
                        routerState.value = IntentRouter(L0Resolver(newIndex), L1Resolver(newIndex), L2Resolver(newIndex), L3Validator(newIndex))
                    }
                }

                // Real routing — re-evaluates when query or router (after real index load) changes
                LaunchedEffect(query, routerState.value) {
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
                        else -> routerState.value.routeToCommandState(query)
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
                        onActExecute = { result ->
                            // Explicit user activation only — never on typing
                            // NO VALIDATED ACTION → NO EXECUTION
                            scope.launch {
                                val validation = L3Validator(currentIndexState.value).validate(result)
                                when (validation) {
                                    is com.aura.resolver.l3.L3ValidationResult.Validated -> {
                                        when (val exec = executor.execute(validation.action)) {
                                            is ExecutionResult.Success -> {
                                                // Success uses existing inline confirmation / success tint — no new state
                                                // For OpenApp, system will switch; for Copy, InlineResult already shows Copied
                                            }
                                            is ExecutionResult.Failure -> {
                                                commandState = CommandState.Error(CommandError(exec.message))
                                            }
                                            is ExecutionResult.Unavailable -> {
                                                commandState = CommandState.Empty(query)
                                            }
                                        }
                                    }
                                    is com.aura.resolver.l3.L3ValidationResult.Invalid -> {
                                        commandState = CommandState.Error(CommandError(validation.message))
                                    }
                                    is com.aura.resolver.l3.L3ValidationResult.Unavailable -> {
                                        commandState = CommandState.Empty(query)
                                    }
                                }
                            }
                        },
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
                        onActionChipClick = { chip ->
                            // Secondary chips are subordinate — for now treat as no-op execution boundary check
                            // Future: map chip to ValidatedAction based on parent result + chip label
                        },
                        onCopy = { text ->
                            scope.launch {
                                val copyResult = ResolvedResult(
                                    id = "copy:${text.hashCode()}",
                                    title = text,
                                    type = ResultType.Math,
                                    action = AuraAction.Copy(text)
                                )
                                val validation = L3Validator(currentIndexState.value).validate(copyResult)
                                if (validation is com.aura.resolver.l3.L3ValidationResult.Validated) {
                                    executor.execute(validation.action)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
