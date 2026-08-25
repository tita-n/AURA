package com.aura

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.design.AuraTheme
import com.aura.domain.*
import com.aura.platform.AndroidAppIndexProvider
import com.aura.platform.android.AndroidContactIndexProvider
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
import com.aura.platform.android.PackageChangeMonitor
import com.aura.resolver.l3.ValidatedAction
import com.aura.ui.library.AppLibraryScreen
import com.aura.ui.notifications.NotificationPanelScreen
import com.aura.platform.android.AndroidNotificationAccessManager
import com.aura.platform.android.NotificationRepository
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
                // Contacts: contextual READ_CONTACTS (PRD §9.8) — loaded only when granted, off-main-thread.
                // Index composition: realApps + realContacts(if granted) + settings. Denied => graceful:
                // apps/calculator/settings/timer keep working; contact queries degrade to Empty.
                val context = androidx.compose.ui.platform.LocalContext.current
                val scope = rememberCoroutineScope()
                val executor = remember(context) { AndroidActionExecutor(context.applicationContext) }
                val contactProvider = remember(context) { AndroidContactIndexProvider(context.applicationContext) }

                fun hasContactsPermission(): Boolean = contactProvider.hasContactsPermission()

                var contactsGranted by remember { mutableStateOf(hasContactsPermission()) }
                var contactsAskedOnce by remember { mutableStateOf(false) }
                val currentIndexState = remember { mutableStateOf(L0IndexFactory.buildIndex(emptyList(), contacts = emptyList())) }
                val routerState = remember {
                    val idx = currentIndexState.value
                    mutableStateOf(IntentRouter(L0Resolver(idx), L1Resolver(idx), L2Resolver(idx), L3Validator(idx)))
                }

                // Contextual permission request — Android's native dialog, triggered only by the first
                // comms-style query. Never at install, never repeated after one ask.
                val contactsPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    contactsGranted = granted
                    if (granted) {
                        scope.launch {
                            val contacts = withContext(Dispatchers.IO) {
                                try { contactProvider.getContactEntities() } catch (_: Exception) { emptyList() }
                            }
                            val newIndex = L0IndexFactory.buildIndex(
                                withContext(Dispatchers.IO) {
                                    try { AndroidAppIndexProvider(context.applicationContext).getAppEntities() } catch (_: Exception) { emptyList() }
                                },
                                contacts = contacts
                            )
                            currentIndexState.value = newIndex
                            routerState.value = IntentRouter(L0Resolver(newIndex), L1Resolver(newIndex), L2Resolver(newIndex), L3Validator(newIndex))
                        }
                    }
                    // Denied: no nag, no rebuild — index stays without contacts; everything else works.
                }

                // Initial load — apps always; contacts only if already granted.
                LaunchedEffect(contactsGranted) {
                    val (realApps, contacts) = withContext(Dispatchers.IO) {
                        val apps: List<com.aura.resolver.IndexedEntity> = try {
                            AndroidAppIndexProvider(context.applicationContext).getAppEntities()
                        } catch (_: Exception) { emptyList() }
                        val cts: List<com.aura.resolver.IndexedEntity> = if (contactsGranted) {
                            try { contactProvider.getContactEntities() } catch (_: Exception) { emptyList() }
                        } else emptyList()
                        apps to cts
                    }
                    val newIndex = L0IndexFactory.buildIndex(realApps, contacts = contacts)
                    currentIndexState.value = newIndex
                    routerState.value = IntentRouter(L0Resolver(newIndex), L1Resolver(newIndex), L2Resolver(newIndex), L3Validator(newIndex))
                }

                // Single explicit-execution path: proposal -> L3 validation -> ValidatedAction -> executor.
                // Called ONLY from explicit user events (Act tap, ACTION chip, Enter on pre-selected Act).
                fun executeValidated(result: ResolvedResult) {
                    scope.launch {
                        when (val validation = L3Validator(currentIndexState.value).validate(result)) {
                            is com.aura.resolver.l3.L3ValidationResult.Validated -> {
                                when (val exec = executor.execute(validation.action)) {
                                    is ExecutionResult.Success -> {
                                        // Execution itself is the success interaction for app-launching
                                        // actions; Copy/Timer keep their existing inline confirmation.
                                    }
                                    is ExecutionResult.Failure ->
                                        commandState = CommandState.Error(CommandError(exec.message))
                                    is ExecutionResult.Unavailable ->
                                        commandState = CommandState.Empty(query)
                                }
                            }
                            is com.aura.resolver.l3.L3ValidationResult.Invalid ->
                                commandState = CommandState.Error(CommandError(validation.message))
                            is com.aura.resolver.l3.L3ValidationResult.Unavailable ->
                                commandState = CommandState.Empty(query)
                        }
                    }
                }

                // Real routing — re-evaluates when query or router (after real index load) changes.
                // Also implements the one-time contextual contacts ask on the first comms query.
                LaunchedEffect(query, routerState.value) {
                    if (!contactsGranted && !contactsAskedOnce &&
                        Regex("^(call|dial|phone|ring|message|text|chat|tell|whatsapp|email|mail|send)\\b", RegexOption.IGNORE_CASE)
                            .containsMatchIn(query.trim())
                    ) {
                        contactsAskedOnce = true
                        contactsPermissionLauncher.launch(AndroidContactIndexProvider.CONTACTS_PERMISSION)
                    }
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

                // ---- Launcher role (PRD \u00a716): detect, offer once per process, never nag after grant/dismissal
                // Android owns the decision; AURA only asks via LauncherRoleHelper.
                var isDefault by remember { mutableStateOf(com.aura.platform.android.LauncherRoleHelper.isDefaultHome(context)) }
                var roleBannerDismissed by remember { mutableStateOf(false) }

                val roleLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { isDefault = com.aura.platform.android.LauncherRoleHelper.isDefaultHome(context) }

                fun requestDefaultHome() {
                    val intent = com.aura.platform.android.LauncherRoleHelper.createRequestIntent(context)
                        ?: com.aura.platform.android.LauncherRoleHelper.homeSettingsIntent()
                    roleLauncher.launch(intent)
                }

                // ---- App Library + package-change refresh (event-driven, no polling)
                var showLibrary by remember { mutableStateOf(false) }
                var showNotifications by remember { mutableStateOf(false) }
                val notifAccess = remember(context) { AndroidNotificationAccessManager(context.applicationContext) }
                var notifGranted by remember { mutableStateOf(notifAccess.isAccessGranted()) }
                val notificationItems by NotificationRepository.items.collectAsState()

                DisposableEffect(context) {
                    val monitor = PackageChangeMonitor(context.applicationContext) {
                        scope.launch {
                            val apps = withContext(Dispatchers.IO) {
                                try { AndroidAppIndexProvider(context.applicationContext).getAppEntities() } catch (_: Exception) { emptyList<com.aura.resolver.IndexedEntity>() }
                            }
                            val contacts = if (contactsGranted) withContext(Dispatchers.IO) {
                                try { contactProvider.getContactEntities() } catch (_: Exception) { emptyList<com.aura.resolver.IndexedEntity>() }
                            } else emptyList()
                            val newIndex = L0IndexFactory.buildIndex(apps, contacts = contacts)
                            currentIndexState.value = newIndex
                            routerState.value = IntentRouter(L0Resolver(newIndex), L1Resolver(newIndex), L2Resolver(newIndex), L3Validator(newIndex))
                        }
                    }
                    monitor.register()
                    onDispose { monitor.unregister() }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AuraTheme.colors.surfaceBase)
                ) {
                    if (showNotifications) {
                        // Re-check on each open — user may have toggled access in Settings.
                        notifGranted = notifAccess.isAccessGranted()
                        if (notifGranted) {
                            // Mark visible keys as seen: only now has the user actually viewed them.
                            LaunchedEffect(Unit) {
                                NotificationRepository.markShown(notificationItems.map { it.key })
                            }
                        }
                        NotificationPanelScreen(
                            items = notificationItems,
                            accessGranted = notifGranted,
                            onRequestAccess = { notifAccess.launchSettings() },
                            onOpenNotification = { item ->
                                // Platform boundary executes the notification's own PendingIntent;
                                // failure stays inside AURA with existing Error semantics.
                                if (!NotificationRepository.open(item.key)) {
                                    commandState = CommandState.Error(CommandError("Cannot open notification"))
                                    showNotifications = false
                                }
                            },
                            onDismissNotification = { item ->
                                NotificationRepository.cancel(item.key)
                            },
                            onClose = { showNotifications = false }
                        )
                    } else if (showLibrary) {
                        AppLibraryScreen(
                            apps = currentIndexState.value.allEntities(),
                            onLaunch = { result ->
                                showLibrary = false
                                executeValidated(result) // same L3+executor path as Command Bar
                            },
                            onClose = { showLibrary = false }
                        )
                    } else {
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
                            executeValidated(result)
                        },
                        onCandidateSelect = { candidate ->
                            // Collapse ASK → ACT (that transition IS confirmation, DL §15)
                            // For app candidates (Did you mean), directly show and execute the chosen app
                            val isApp = candidate.id.startsWith("app:")
                            val isSettings = candidate.id.startsWith("settings:")
                            val result = when {
                                isApp -> {
                                    val pkg = candidate.id.removePrefix("app:")
                                    ResolvedResult(
                                        id = candidate.id,
                                        title = candidate.title,
                                        subtitle = candidate.disambiguation,
                                        type = ResultType.App,
                                        action = AuraAction.OpenApp(pkg)
                                    )
                                }
                                isSettings -> {
                                    val key = candidate.id.removePrefix("settings:")
                                    ResolvedResult(
                                        id = candidate.id,
                                        title = candidate.title,
                                        subtitle = candidate.disambiguation,
                                        type = ResultType.Settings,
                                        action = AuraAction.OpenSettings(key)
                                    )
                                }
                                else -> ResolvedResult(
                                    id = candidate.id,
                                    title = candidate.title,
                                    subtitle = candidate.disambiguation,
                                    type = ResultType.Contact,
                                    action = AuraAction.NoOp
                                )
                            }
                            commandState = CommandState.Act(result)
                            // For apps/settings, execute immediately on selection (one-tap open)
                            if (isApp || isSettings) {
                                scope.launch {
                                    val validation = L3Validator(currentIndexState.value).validate(result)
                                    if (validation is com.aura.resolver.l3.L3ValidationResult.Validated) {
                                        when (val exec = executor.execute(validation.action)) {
                                            is ExecutionResult.Failure -> commandState = CommandState.Error(CommandError(exec.message))
                                            is ExecutionResult.Unavailable -> commandState = CommandState.Empty(query)
                                            else -> {}
                                        }
                                    }
                                }
                            }
                        },
                        onActionChipClick = { chip ->
                            // Explicit execution event: chip maps to a sibling action of the same
                            // validated parent; still passes through L3 before executing.
                            val current = commandState
                            if (current is CommandState.Act) {
                                ActionChipMapper.map(current.result, chip)?.let { mapped ->
                                    commandState = CommandState.Act(mapped)
                                    executeValidated(mapped)
                                }
                            }
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
                        },
                        onUndo = {
                            // Dismisses the inline confirmation — returns to idle (DL §15).
                            // Never claims system-timer cancellation AURA cannot perform.
                            commandState = CommandState.Idle
                            query = ""
                        },
                        onSubmit = {
                            // Design Direction: top result is pre-selected — Enter executes it.
                            // Only an existing ACT may execute; typing alone never does.
                            val current = commandState
                            if (current is CommandState.Act) {
                                executeValidated(current.result)
                            }
                        },
                        onOpenNotifications = { showNotifications = true },
                        showDefaultHomeBanner = !isDefault && !roleBannerDismissed,
                        onSetAsDefault = { requestDefaultHome() },
                        onDismissRoleBanner = { roleBannerDismissed = true },
                        onOpenLibrary = { showLibrary = true }
                    )
                    }
                }
            }
        }
    }
}
