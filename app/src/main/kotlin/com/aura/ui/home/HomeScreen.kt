package com.aura.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aura.design.AuraTheme
import com.aura.design.auraFocusRing
import com.aura.domain.*
import com.aura.home.BatteryUiModel
import com.aura.home.DockItem
import com.aura.home.HomeModuleType
import com.aura.home.NextEventInfo
import com.aura.home.Presence
import com.aura.platform.AppIcon
import com.aura.resolver.IndexedEntity
import com.aura.ui.command.CommandBar
import com.aura.ui.command.CommandStateHost
import com.aura.ui.components.AuraChip
import com.aura.ui.components.ChipVariant
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * HomeScreen — shell per PRD 9.1 and Design Direction §4.2.
 * Vertically composed, generous whitespace, no idle animation.
 *
 * Regions (stable, never free-form):
 *   Time/Presence → optional modules → widget slots → library affordance
 *   → resolution surface → CommandBar (fixed, always reachable) → Dock.
 *
 * Layout cap: Command Bar remains in lower-middle third; editing never blocks it.
 */
@Composable
fun HomeScreen(
    commandState: CommandState = CommandState.Idle,
    isResolving: Boolean = false,
    onQueryChange: (String) -> Unit = {},
    query: String = "",
    focused: Boolean = false,
    onFocusedChange: (Boolean) -> Unit = {},
    presenceText: String? = null,
    onActExecute: (ResolvedResult) -> Unit = {},
    onCandidateSelect: (CandidateItemData) -> Unit = {},
    onActionChipClick: (ActionChipData) -> Unit = {},
    onCopy: (String) -> Unit = {},
    onUndo: () -> Unit = {},
    onSubmit: () -> Unit = {},
    showDefaultHomeBanner: Boolean = false,
    onSetAsDefault: () -> Unit = {},
    onDismissRoleBanner: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    // Home experience params
    dock: List<DockItem> = emptyList(),
    appIndex: List<IndexedEntity> = emptyList(),
    onDockLaunch: (String) -> Unit = {},
    modules: List<HomeModuleType> = emptyList(),
    nextEvent: NextEventInfo? = null,
    nextEventPermissionDenied: Boolean = false,
    onRequestNextEventPermission: () -> Unit = {},
    battery: BatteryUiModel? = null,
    musicPlaying: Boolean = false,
    onMusicPlayPause: () -> Unit = {},
    onMusicNext: () -> Unit = {},
    onMusicPrev: () -> Unit = {},
    widgetIds: List<Int> = emptyList(),
    widgetContent: @Composable (Int) -> Unit = {},
    wallpaperEnabled: Boolean = false,
    onOpenEdit: () -> Unit = {}
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    var internalQuery by remember { mutableStateOf(query) }
    var internalFocused by remember { mutableStateOf(focused) }
    LaunchedEffect(query) { internalQuery = query }
    LaunchedEffect(focused) { internalFocused = focused }
    val nowMillis = rememberMinuteTicker()
    val hourOfDay = remember(nowMillis) { Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY) }
    val effectivePresence = presenceText ?: Presence.greetingFor(hourOfDay)
    val appByPackage: Map<String, IndexedEntity> = remember(appIndex) {
        appIndex.associateBy { it.id.removePrefix("app:") }
    }

    Box(Modifier.fillMaxSize()) {
        // Wallpaper layer — only when enabled; flat scrim preserves contrast
        if (wallpaperEnabled) {
            WallpaperScrimLayer(timeTextColor = colors.textPrimary)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfaceBase.copy(alpha = if (wallpaperEnabled) 0.06f else 1f))
                .padding(horizontal = AuraTheme.spacing.screenEdge)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // Time/Presence header — long-press to edit
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { onOpenEdit() })
                    },
                contentAlignment = Alignment.Center
            ) {
                TimeAndPresenceBlock(
                    nowMillis = nowMillis,
                    presenceText = effectivePresence,
                    wallpaperEnabled = wallpaperEnabled
                )
            }

            // Optional native modules — scrollable if present + widgets
            val hasExtras = modules.isNotEmpty() || widgetIds.isNotEmpty()
            if (hasExtras) {
                Spacer(Modifier.height(16.dp))
                val listModifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { onOpenEdit() })
                    }
                LazyColumn(
                    modifier = listModifier,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Native modules in persisted order
                    items(modules, key = { "mod:${it.name}" }) { mod ->
                        when (mod) {
                            HomeModuleType.NextEvent -> NextEventRow(
                                event = nextEvent,
                                denied = nextEventPermissionDenied,
                                onRequestPermission = onRequestNextEventPermission
                            )
                            HomeModuleType.Battery -> BatteryRow(battery = battery)
                            HomeModuleType.Music -> MusicRow(
                                playing = musicPlaying,
                                onPlayPause = onMusicPlayPause,
                                onNext = onMusicNext,
                                onPrev = onMusicPrev
                            )
                        }
                    }
                    // Widget slots (AndroidView per id, stable key)
                    items(widgetIds, key = { "wid:$it" }) { id ->
                        WidgetSlotCard(widgetId = id, content = widgetContent)
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            // Role banner
            if (showDefaultHomeBanner) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Make AURA your Home", style = typography.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    AuraChip(variant = ChipVariant.Action("Set as default"), onClick = onSetAsDefault)
                    AuraChip(variant = ChipVariant.Secondary("Not now"), onClick = onDismissRoleBanner)
                }
                Spacer(Modifier.height(16.dp))
            }

            // Library affordance — tap/swipe up for App Library; long-press to edit elsewhere
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clickable(role = Role.Button, onClick = onOpenLibrary),
                contentAlignment = Alignment.Center
            ) {
                Text("⌃  Apps", style = typography.caption, color = colors.textSecondary.copy(alpha = 0.6f))
            }

            // Resolution surface
            if (commandState !is CommandState.Idle || isResolving) {
                CommandStateHost(
                    state = commandState,
                    isResolving = isResolving,
                    onActExecute = onActExecute,
                    onCandidateSelect = onCandidateSelect,
                    onActionChipClick = onActionChipClick,
                    onCopy = onCopy,
                    onUndo = onUndo,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
            }

            // CommandBar — fixed 56dp, never movable
            CommandBar(
                query = internalQuery,
                onQueryChange = { internalQuery = it; onQueryChange(it) },
                focused = internalFocused,
                onFocusedChange = { internalFocused = it; onFocusedChange(it) },
                onClear = { internalQuery = ""; onQueryChange("") },
                onSubmit = onSubmit,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            DockBar(dock = dock, appByPackage = appByPackage, onLaunch = onDockLaunch)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TimeAndPresenceBlock(
    nowMillis: Long,
    presenceText: String?,
    wallpaperEnabled: Boolean
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    val timeText = remember(nowMillis) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMillis)) }
    val dateText = remember(nowMillis) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(nowMillis)) }
    val secondary = if (wallpaperEnabled) colors.textPrimary.copy(alpha = 0.72f) else colors.textSecondary
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(timeText, style = typography.display, color = colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text(dateText, style = typography.caption, color = secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (presenceText != null) {
            Spacer(Modifier.height(8.dp))
            Text(presenceText, style = typography.body, color = secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NextEventRow(
    event: NextEventInfo?,
    denied: Boolean,
    onRequestPermission: () -> Unit
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    when {
        denied -> Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(AuraTheme.radius.small))
                .background(colors.surfaceRaised)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(AuraTheme.radius.small))
                .padding(AuraTheme.spacing.componentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("📅", style = typography.body)
            Column(Modifier.weight(1f)) {
                Text("Calendar is hidden", style = typography.body, color = colors.textPrimary)
                Text("Tap to allow — no data leaves the device.", style = typography.caption, color = colors.textSecondary)
            }
            AuraChip(variant = ChipVariant.Action("Allow"), onClick = onRequestPermission)
        }
        event == null -> Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(AuraTheme.radius.small))
                .background(colors.surfaceRaised)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(AuraTheme.radius.small))
                .padding(AuraTheme.spacing.componentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("📅", style = typography.body)
            Text("No upcoming events", style = typography.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
        }
        else -> {
            val whenText = remember(event.beginMillis, event.endMillis) {
                formatEventWhen(event)
            }
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(AuraTheme.radius.small))
                    .background(colors.surfaceRaised)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(AuraTheme.radius.small))
                    .padding(AuraTheme.spacing.componentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceBase), contentAlignment = Alignment.Center) {
                    Text("📅", style = typography.caption)
                }
                Column(Modifier.weight(1f)) {
                    Text(event.title, style = typography.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(whenText, style = typography.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun formatEventWhen(e: NextEventInfo): String {
    val now = System.currentTimeMillis()
    val isNow = e.beginMillis <= now && now <= e.endMillis
    val tf = SimpleDateFormat(if (e.allDay) "EEE d MMM" else "EEE d MMM · HH:mm", Locale.getDefault())
    return if (isNow) "Now — ${e.title}" else tf.format(Date(e.beginMillis))
}

@Composable
private fun BatteryRow(battery: BatteryUiModel?) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    val label = when {
        battery == null -> "Battery unavailable"
        battery.charging -> "${battery.percent}% · charging"
        else -> "${battery.percent}%"
    }
    val icon = if (battery?.charging == true) "⚡" else "◐"
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(AuraTheme.radius.small))
            .background(colors.surfaceRaised)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(AuraTheme.radius.small))
            .padding(AuraTheme.spacing.componentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceBase), contentAlignment = Alignment.Center) {
            Text(icon, style = typography.caption)
        }
        Text(label, style = typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MusicRow(
    playing: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(AuraTheme.radius.small))
            .background(colors.surfaceRaised)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(AuraTheme.radius.small))
            .padding(AuraTheme.spacing.componentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceBase), contentAlignment = Alignment.Center) {
            Text(if (playing) "♪" else "𝄞", style = typography.caption)
        }
        Text(if (playing) "Playing" else "Paused", style = typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
        AuraChip(variant = ChipVariant.Secondary("◀"), onClick = onPrev)
        AuraChip(variant = ChipVariant.Action(if (playing) "❚❚" else "▶"), onClick = onPlayPause)
        AuraChip(variant = ChipVariant.Secondary("▶"), onClick = onNext)
    }
}

@Composable
private fun WidgetSlotCard(
    widgetId: Int,
    content: @Composable (Int) -> Unit
) {
    val colors = AuraTheme.colors
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(AuraTheme.radius.small))
            .background(colors.surfaceRaised)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(AuraTheme.radius.small))
            .padding(4.dp)
    ) {
        content(widgetId)
    }
}

@Composable
private fun DockBar(
    dock: List<DockItem>,
    appByPackage: Map<String, IndexedEntity>,
    onLaunch: (String) -> Unit
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    if (dock.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            Text("Dock empty — edit to add apps", style = typography.caption, color = colors.textSecondary.copy(alpha = 0.5f))
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        dock.forEach { item ->
            val entity = appByPackage[item.packageName]
            val label = entity?.displayLabel ?: item.packageName.substringAfterLast(".")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(colors.surfaceRaised)
                        .border(1.dp, colors.borderSubtle, CircleShape)
                        .clickable(role = Role.Button, onClick = { onLaunch(item.packageName) })
                        .auraFocusRing(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(packageName = item.packageName, label = label, contentDescription = label, modifier = Modifier)
                }
                if (dock.size <= 3) {
                    Spacer(Modifier.height(4.dp))
                    Text(label, style = typography.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun WallpaperScrimLayer(timeTextColor: androidx.compose.ui.graphics.Color) {
    // Wallpaper lives behind Home content; system wallpaper is managed by the OS.
    // The flat scrim (surfaceBase at ~94% alpha) preserves AA contrast vs any wallpaper.
    // Drawn as a background behind the entire Home column.
    Box(Modifier.fillMaxSize().background(AuraTheme.colors.surfaceBase.copy(alpha = 0.94f)))
}

@Composable
private fun rememberMinuteTicker(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            val sec = cal.get(Calendar.SECOND)
            val ms = cal.get(Calendar.MILLISECOND)
            val remain = 60_000 - (sec * 1000L + ms)
            kotlinx.coroutines.delay(remain.coerceAtLeast(200L))
            now = System.currentTimeMillis()
        }
    }
    return now
}
