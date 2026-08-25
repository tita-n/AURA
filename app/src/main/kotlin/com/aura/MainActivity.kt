package com.aura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aura.design.AuraTheme
import com.aura.design.LocalReducedMotion
import com.aura.domain.*
import com.aura.home.*
import com.aura.platform.AndroidAppIndexProvider
import com.aura.platform.android.AndroidContactIndexProvider
import com.aura.platform.android.AuraPrefs
import com.aura.platform.android.BatteryMonitor
import com.aura.platform.android.MusicMonitor
import com.aura.platform.android.NextEventProvider
import com.aura.platform.android.AuraWidgetHost
import com.aura.platform.android.WallpaperPicker
import com.aura.resolver.IntentRouter
import com.aura.resolver.L0IndexFactory
import com.aura.resolver.L0Resolver
import com.aura.resolver.l1.L1Resolver
import com.aura.resolver.l2.L2Resolver
import com.aura.resolver.l3.L3Validator
import com.aura.home.EditSurface
import com.aura.ui.home.HomeEditOverlay
import com.aura.ui.home.HomeScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.rememberCoroutineScope
import com.aura.platform.android.AndroidActionExecutor
import com.aura.platform.android.ExecutionResult
import com.aura.platform.android.PackageChangeMonitor
import com.aura.ui.library.AppLibraryScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.viewinterop.AndroidView
import android.os.Bundle as AndroidBundle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val executor = remember(context) { AndroidActionExecutor(context.applicationContext) }
            val contactProvider = remember(context) { AndroidContactIndexProvider(context.applicationContext) }
            val auraPrefs = remember(context) { AuraPrefs(context.applicationContext) }
            val homeSettings by auraPrefs.settings.collectAsState()
            val isDarkTheme = when (homeSettings.customization.themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> isSystemInDarkTheme()
            }
            val reducedMotion = homeSettings.customization.animationIntensity == AnimationIntensity.Reduced
            val accentOverride: Color? = when (val a = homeSettings.customization.accent) {
                is AccentChoice.Dynamic -> null
                is AccentChoice.Curated -> {
                    val pair = AccentPalette.entries.getOrNull(a.index) ?: AccentPalette.entries.first()
                    Color(if (isDarkTheme) pair.first else pair.second)
                }
            }

            // Wallpaper window flag — show system wallpaper behind AURA when enabled.
            // Without this, Window background is opaque and wallpaper never shows (user report: black/white only).
            val wallpaperEnabled = homeSettings.customization.showWallpaper
            val activityWindow = (context as? android.app.Activity)?.window
            LaunchedEffect(wallpaperEnabled, isDarkTheme) {
                val w = activityWindow ?: return@LaunchedEffect
                if (wallpaperEnabled) {
                    w.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                    w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                } else {
                    w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                    val bg = if (isDarkTheme) 0xFF0A0A0B.toInt() else 0xFFFAF9F6.toInt()
                    w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bg))
                }
            }

            // ---- Widget host ----
            val widgetHost = remember(context) { AuraWidgetHost(context.applicationContext) }
            val installedWidgetProviders by widgetHost.installed.collectAsState()
            val activityLifecycle = this@MainActivity.lifecycle
            DisposableEffect(activityLifecycle) {
                widgetHost.startListening()
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) widgetHost.startListening()
                    if (event == Lifecycle.Event.ON_STOP) widgetHost.stopListening()
                }
                activityLifecycle.addObserver(obs)
                onDispose {
                    activityLifecycle.removeObserver(obs)
                    widgetHost.stopListening()
                }
            }
            // Prune orphaned host ids not in stored list (covers uninstalls)
            LaunchedEffect(homeSettings.widgetIds) {
                widgetHost.deleteOrphaned(homeSettings.widgetIds)
            }

            // Pending widget allocate/bind/configure state
            var pendingWidgetId by remember { mutableStateOf<Int?>(null) }
            var pendingWidgetProvider by remember { mutableStateOf<android.content.ComponentName?>(null) }

            val widgetConfigureLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val id = pendingWidgetId
                pendingWidgetId = null
                pendingWidgetProvider = null
                if (id != null && id != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) {
                    if (result.resultCode == RESULT_OK) {
                        val next = homeSettings.widgetIds + id
                        auraPrefs.setWidgetIds(next)
                    } else {
                        widgetHost.deleteId(id)
                    }
                }
            }

            val widgetBindLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val id = pendingWidgetId
                val provider = pendingWidgetProvider
                if (id != null && provider != null && result.resultCode == RESULT_OK) {
                    // Bound — now maybe configure
                    val info = installedWidgetProviders.firstOrNull { it.provider == provider }
                        ?: widgetHost.providerForId(id)?.let { widgetHost.providerForId(id) } // fallback lookup
                    // Re-query info via manager
                    val resolved = try { widgetHost.providerForId(id) } catch (_: Exception) { null }
                    val cfg = resolved?.let { widgetHost.configureIntent(it, id) }
                    if (cfg != null) {
                        widgetConfigureLauncher.launch(cfg)
                    } else {
                        auraPrefs.setWidgetIds(homeSettings.widgetIds + id)
                        pendingWidgetId = null
                        pendingWidgetProvider = null
                    }
                } else if (id != null) {
                    if (id != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) widgetHost.deleteId(id)
                    pendingWidgetId = null
                    pendingWidgetProvider = null
                }
            }

            fun addWidgetFlow(provider: android.appwidget.AppWidgetProviderInfo) {
                val id = widgetHost.allocateId()
                if (id == android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) return
                val bound = widgetHost.bindIfAllowed(id, provider.provider)
                if (!bound) {
                    pendingWidgetId = id
                    pendingWidgetProvider = provider.provider
                    val bindIntent = widgetHost.bindPermissionIntent(id, provider.provider)
                    try { widgetBindLauncher.launch(bindIntent) } catch (_: Exception) {
                        widgetHost.deleteId(id); pendingWidgetId = null; pendingWidgetProvider = null
                    }
                    return
                }
                val cfg = widgetHost.configureIntent(provider, id)
                if (cfg != null) {
                    pendingWidgetId = id
                    pendingWidgetProvider = provider.provider
                    try { widgetConfigureLauncher.launch(cfg) } catch (_: Exception) {
                        auraPrefs.setWidgetIds(homeSettings.widgetIds + id)
                        pendingWidgetId = null; pendingWidgetProvider = null
                    }
                } else {
                    auraPrefs.setWidgetIds(homeSettings.widgetIds + id)
                }
            }

            // ---- Battery ----
            val batteryMonitor = remember(context) { BatteryMonitor(context.applicationContext) }
            val batteryState by batteryMonitor.state.collectAsState()
            DisposableEffect(batteryMonitor) {
                batteryMonitor.start()
                onDispose { batteryMonitor.stop() }
            }

            // ---- Music ----
            val musicMonitor = remember(context) { MusicMonitor(context.applicationContext) }
            val musicPlaying by musicMonitor.playing.collectAsState()
            DisposableEffect(activityLifecycle) {
                val obs = LifecycleEventObserver { _, e ->
                    if (e == Lifecycle.Event.ON_RESUME) musicMonitor.refresh()
                }
                activityLifecycle.addObserver(obs)
                musicMonitor.refresh()
                onDispose { activityLifecycle.removeObserver(obs) }
            }

            // ---- Calendar / Next Event ----
            val nextEventProvider = remember(context) { NextEventProvider(context.applicationContext) }
            var nextEvent by remember { mutableStateOf<NextEventInfo?>(null) }
            var calendarGranted by remember { mutableStateOf(nextEventProvider.hasPermission()) }
            var calendarAskedOnce by remember { mutableStateOf(false) }
            val nextEventVersion by nextEventProvider.version.collectAsState()
            DisposableEffect(nextEventProvider) {
                if (HomeModuleType.NextEvent in homeSettings.modules) nextEventProvider.startObserving()
                onDispose { nextEventProvider.stopObserving() }
            }
            // Reflect enable/disable of Next Event observing
            LaunchedEffect(homeSettings.modules) {
                if (HomeModuleType.NextEvent in homeSettings.modules) nextEventProvider.startObserving()
                else nextEventProvider.stopObserving()
                calendarGranted = nextEventProvider.hasPermission()
            }
            val calendarPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                calendarGranted = granted
                calendarAskedOnce = true
            }
            // Auto-ask once when Next Event becomes enabled and permission not granted
            LaunchedEffect(homeSettings.modules, calendarGranted, calendarAskedOnce) {
                if (HomeModuleType.NextEvent in homeSettings.modules && !calendarGranted && !calendarAskedOnce) {
                    calendarAskedOnce = true
                    calendarPermissionLauncher.launch(NextEventProvider.PERMISSION)
                }
            }
            // Query next event when version/permission changes (IO thread)
            LaunchedEffect(nextEventVersion, calendarGranted, homeSettings.modules) {
                if (HomeModuleType.NextEvent !in homeSettings.modules || !calendarGranted) {
                    nextEvent = null
                    return@LaunchedEffect
                }
                val ev = withContext(Dispatchers.IO) { nextEventProvider.queryNextEvent() }
                nextEvent = ev
            }

            // ---- Wallpaper picker ----
            val wallpaperLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { }
            fun launchWallpaperPicker() {
                val intent = WallpaperPicker.intent(context) ?: return
                try { wallpaperLauncher.launch(intent) } catch (_: Exception) {}
            }

            fun hasContactsPermission(): Boolean = contactProvider.hasContactsPermission()
            var contactsGranted by remember { mutableStateOf(hasContactsPermission()) }
            var contactsAskedOnce by remember { mutableStateOf(false) }
            val currentIndexState = remember { mutableStateOf(L0IndexFactory.buildIndex(emptyList(), contacts = emptyList())) }
            val routerState = remember {
                val idx = currentIndexState.value
                mutableStateOf(IntentRouter(L0Resolver(idx), L1Resolver(idx), L2Resolver(idx), L3Validator(idx)))
            }

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
            }

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

            var query by remember { mutableStateOf("") }
            var commandState: CommandState by remember { mutableStateOf(CommandState.Idle) }
            var focused by remember { mutableStateOf(false) }
            var isResolving by remember { mutableStateOf(false) }

            fun executeValidated(result: ResolvedResult) {
                scope.launch {
                    when (val validation = L3Validator(currentIndexState.value).validate(result)) {
                        is com.aura.resolver.l3.L3ValidationResult.Validated -> {
                            when (val exec = executor.execute(validation.action)) {
                                is ExecutionResult.Success -> {}
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
                    query.equals("error", ignoreCase = true) -> CommandState.Error(PreviewData.errorExample)
                    else -> routerState.value.routeToCommandState(query)
                }
            }

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

            var showLibrary by remember { mutableStateOf(false) }
            var editSurface: EditSurface by remember { mutableStateOf(EditSurface.Closed) }

            // Prune dock_WIDGET ids on package/uninstall events: handled in monitor callback + host prune above
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
                        // Prune dock entries whose app was uninstalled
                        val installed = apps.map { it.id.removePrefix("app:") }.toSet()
                        val prunedDock = DockLogic.prune(auraPrefs.settings.value.dock, installed)
                        if (prunedDock != auraPrefs.settings.value.dock) auraPrefs.setDock(prunedDock)
                        // Prune stale widget ids (provider no longer available)
                        val liveIds = widgetHost.hostIds()
                        val stored = auraPrefs.settings.value.widgetIds
                        val prunedWidgets = stored.filter { it in liveIds && widgetHost.providerForId(it) != null }
                        if (prunedWidgets != stored) auraPrefs.setWidgetIds(prunedWidgets)
                    }
                }
                monitor.register()
                onDispose { monitor.unregister() }
            }

            // Derive widget id -> label map for edit sheet rows
            val widgetIdLabels: Map<Int, String> = remember(homeSettings.widgetIds, installedWidgetProviders) {
                val byId = mutableMapOf<Int, String>()
                homeSettings.widgetIds.forEach { id ->
                    val info = try { widgetHost.providerForId(id) } catch (_: Exception) { null }
                    val label = info?.let {
                        try { it.loadLabel(packageManager) } catch (_: Exception) { it.provider.packageName }
                    } ?: "Widget #$id"
                    byId[id] = label
                }
                byId
            }

            // Use custom accent color validated at render time via AuraTheme resolver
            androidx.compose.runtime.CompositionLocalProvider(
                LocalReducedMotion provides reducedMotion
            ) {
                AuraTheme(darkTheme = isDarkTheme, accentOverride = accentOverride) {
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
                            onActExecute = { result -> executeValidated(result) },
                            onCandidateSelect = { candidate ->
                                val isApp = candidate.id.startsWith("app:")
                                val isSettings = candidate.id.startsWith("settings:")
                                val result = when {
                                    isApp -> {
                                        val pkg = candidate.id.removePrefix("app:")
                                        ResolvedResult(
                                            id = candidate.id, title = candidate.title, subtitle = candidate.disambiguation,
                                            type = ResultType.App, action = AuraAction.OpenApp(pkg)
                                        )
                                    }
                                    isSettings -> {
                                        val key = candidate.id.removePrefix("settings:")
                                        ResolvedResult(
                                            id = candidate.id, title = candidate.title, subtitle = candidate.disambiguation,
                                            type = ResultType.Settings, action = AuraAction.OpenSettings(key)
                                        )
                                    }
                                    else -> ResolvedResult(
                                        id = candidate.id, title = candidate.title, subtitle = candidate.disambiguation,
                                        type = ResultType.Contact, action = AuraAction.NoOp
                                    )
                                }
                                commandState = CommandState.Act(result)
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
                                        id = "copy:${text.hashCode()}", title = text, type = ResultType.Math,
                                        action = AuraAction.Copy(text)
                                    )
                                    val validation = L3Validator(currentIndexState.value).validate(copyResult)
                                    if (validation is com.aura.resolver.l3.L3ValidationResult.Validated) executor.execute(validation.action)
                                }
                            },
                            onUndo = { commandState = CommandState.Idle; query = "" },
                            onSubmit = {
                                val current = commandState
                                if (current is CommandState.Act) executeValidated(current.result)
                            },
                            showDefaultHomeBanner = !isDefault && !roleBannerDismissed,
                            onSetAsDefault = { requestDefaultHome() },
                            onDismissRoleBanner = { roleBannerDismissed = true },
                            onOpenLibrary = { showLibrary = true },
                            dock = homeSettings.dock,
                            appIndex = currentIndexState.value.allEntities(),
                            onDockLaunch = { pkg ->
                                val fake = ResolvedResult(id = "app:$pkg", title = pkg, type = ResultType.App, action = AuraAction.OpenApp(pkg))
                                executeValidated(fake)
                            },
                            modules = homeSettings.modules,
                            nextEvent = nextEvent,
                            nextEventPermissionDenied = HomeModuleType.NextEvent in homeSettings.modules && !calendarGranted,
                            onRequestNextEventPermission = {
                                if (!calendarGranted) calendarPermissionLauncher.launch(NextEventProvider.PERMISSION)
                            },
                            battery = batteryState,
                            musicPlaying = musicPlaying,
                            onMusicPlayPause = { musicMonitor.playPause(); scope.launch { kotlinx.coroutines.delay(350); musicMonitor.refresh() } },
                            onMusicNext = { musicMonitor.next(); scope.launch { kotlinx.coroutines.delay(350); musicMonitor.refresh() } },
                            onMusicPrev = { musicMonitor.prev(); scope.launch { kotlinx.coroutines.delay(350); musicMonitor.refresh() } },
                            widgetIds = homeSettings.widgetIds,
                            widgetContent = { wid ->
                                val providerInfo = remember(wid) {
                                    try { widgetHost.providerForId(wid) } catch (_: Exception) { null }
                                }
                                if (providerInfo == null) {
                                    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                        Text("Widget unavailable", style = AuraTheme.typography.caption, color = AuraTheme.colors.textSecondary)
                                    }
                                } else {
                                    val density = LocalContext.current.resources.displayMetrics.density
                                    var hostViewRef by remember { mutableStateOf<android.appwidget.AppWidgetHostView?>(null) }
                                    AndroidView(
                                        factory = { ctx ->
                                            val v = widgetHost.createView(wid, providerInfo)
                                            hostViewRef = v
                                            v ?: android.view.View(ctx)
                                        },
                                        update = { view ->
                                            if (view is android.appwidget.AppWidgetHostView) {
                                                val w = (view.width / density).toInt().coerceAtLeast(120)
                                                val h = (view.height / density).toInt().coerceAtLeast(96)
                                                if (w > 0 && h > 0) widgetHost.updateSize(view, w, h)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
                                            .wrapContentHeight()
                                    )
                                }
                            },
                            wallpaperEnabled = homeSettings.customization.showWallpaper,
                            onOpenEdit = { editSurface = EditSurface.Main }
                        )

                        if (showLibrary) {
                            AppLibraryScreen(
                                apps = currentIndexState.value.allEntities(),
                                onLaunch = { result -> showLibrary = false; executeValidated(result) },
                                onClose = { showLibrary = false }
                            )
                        }

                        if (editSurface !is EditSurface.Closed) {
                            HomeEditOverlay(
                                surface = editSurface,
                                settings = homeSettings,
                                onSettingsChange = { auraPrefs.setSettings(it) },
                                appIndex = currentIndexState.value.allEntities(),
                                widgetLabels = widgetIdLabels,
                                installedWidgetProviders = installedWidgetProviders,
                                onAddWidget = { info -> addWidgetFlow(info) },
                                onChooseWallpaper = { launchWallpaperPicker() },
                                onClose = { editSurface = EditSurface.Closed },
                                onOpenDockPicker = { editSurface = EditSurface.DockPicker },
                                onOpenWidgetPicker = { editSurface = EditSurface.WidgetPicker },
                                onBackToMain = { editSurface = EditSurface.Main }
                            )
                        }
                    }
                }
            }
        }
    }
}
