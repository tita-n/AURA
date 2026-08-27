package com.aura.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.BitmapFactory
import com.aura.design.AuraTheme
import com.aura.design.auraFocusRing
import com.aura.domain.*
import com.aura.home.BatteryContextualItem
import com.aura.home.BatteryUiModel
import com.aura.home.CalendarContextualItem
import com.aura.home.ContextualEngine
import com.aura.home.ContextualItem
import com.aura.home.DockItem
import com.aura.home.HomeModuleType
import com.aura.home.MusicContextualItem
import com.aura.home.MusicState
import com.aura.home.NextEventInfo
import com.aura.home.Presence
import com.aura.home.WallpaperBrightness
import com.aura.home.WallpaperTreatment
import com.aura.home.WallpaperTreatmentResolver
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
import kotlinx.coroutines.delay

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
    music: MusicState? = null,
    musicPlaying: Boolean = false,
    musicAccess: Boolean = false,
    onMusicPlayPause: () -> Unit = {},
    onMusicNext: () -> Unit = {},
    onMusicPrev: () -> Unit = {},
    widgetIds: List<Int> = emptyList(),
    widgetContent: @Composable (Int) -> Unit = {},
    wallpaperEnabled: Boolean = false,
    wallpaperTreatment: WallpaperTreatment? = null,
    reducedMotion: Boolean = false,
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
    val haptics = LocalHapticFeedback.current

    Box(Modifier.fillMaxSize()) {
        if (wallpaperEnabled) {
            // Wallpaper is the system wallpaper behind the transparent activity window
            // (FLAG_SHOW_WALLPAPER in MainActivity). We darken it with a vertical scrim:
            // top = adaptive alpha for the wallpaper class, bottom slightly stronger so the
            // Command Bar / dock stay readable on bright wallpapers. The wallpaper itself
            // remains visible — AURA only controls the darkening.
            val treatment = wallpaperTreatment
                ?: WallpaperTreatment(WallpaperBrightness.Bright, WallpaperTreatmentResolver.ALPHA_BRIGHT)
            val topAlpha = treatment.scrimAlpha.coerceIn(0f, 1f)
            val bottomAlpha = WallpaperTreatmentResolver.bottomAlpha(treatment).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.surfaceBase.copy(alpha = topAlpha),
                            colors.surfaceBase.copy(alpha = bottomAlpha)
                        )
                    )
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (wallpaperEnabled) androidx.compose.ui.graphics.Color.Transparent else colors.surfaceBase)
                .padding(horizontal = AuraTheme.spacing.screenEdge)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // Time/Presence header — long-press to edit
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(haptics) {
                        detectTapGestures(onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenEdit()
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                TimeAndPresenceBlock(
                    nowMillis = nowMillis,
                    presenceText = effectivePresence,
                    wallpaperEnabled = wallpaperEnabled
                )
            }

            // ONE contextual surface. Next Event / Battery / Music feed a single card that
            // appears only when something is relevant and rotates when several are. Enabled
            // sources are *allowed* to generate info; they do not permanently occupy Home.
            // Relevance + priority are pure (ContextualEngine) — see PRODUCT.md.
            val nextEventEnabled = HomeModuleType.NextEvent in modules
            val batteryEnabled = HomeModuleType.Battery in modules
            val musicEnabled = HomeModuleType.Music in modules
            val contextualItems = remember(
                nowMillis, nextEvent, nextEventPermissionDenied, nextEventEnabled,
                battery, batteryEnabled, music, musicEnabled
            ) {
                ContextualEngine.build(
                    nowMillis = nowMillis,
                    nextEvent = nextEvent,
                    nextEventDenied = nextEventPermissionDenied,
                    nextEventEnabled = nextEventEnabled,
                    battery = battery,
                    batteryEnabled = batteryEnabled,
                    musicState = music ?: MusicState.Hidden,
                    musicEnabled = musicEnabled,
                    musicAccess = musicAccess
                )
            }

            // Optional extras (contextual surface + widgets) — scrollable if present
            val hasExtras = contextualItems.isNotEmpty() || widgetIds.isNotEmpty()
            if (hasExtras) {
                Spacer(Modifier.height(16.dp))
                val listModifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(haptics) {
                        detectTapGestures(onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenEdit()
                        })
                    }
                LazyColumn(
                    modifier = listModifier,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (contextualItems.isNotEmpty()) {
                        item(key = "ctx") {
                            ContextualSurface(
                                items = contextualItems,
                                reducedMotion = reducedMotion,
                                onMusicPlayPause = onMusicPlayPause,
                                onMusicNext = onMusicNext,
                                onMusicPrev = onMusicPrev,
                                onRequestNextEventPermission = onRequestNextEventPermission
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

            // Library affordance — dedicated 48dp tappable entry (Button semantics) + swipe-up.
            // Not inside the middle long-press region so OEM gesture overlays cannot swallow it.
            // Restrained: caption glyph + label, no giant bar, tokens only.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(AuraTheme.radius.small))
                    .auraFocusRing(androidx.compose.foundation.shape.RoundedCornerShape(AuraTheme.radius.small))
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -24f) onOpenLibrary()
                        }
                    }
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Open app library",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenLibrary()
                        }
                    )
                    .semantics { contentDescription = "Open app library" }
                    .padding(vertical = 12.dp),
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
                onSubmit = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSubmit()
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            DockBar(dock = dock, appByPackage = appByPackage, onLaunch = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDockLaunch(it)
            })
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
    // When wallpaper is visible, all home text uses textPrimary so contrast holds with the
    // adaptive scrim (>=0.70 on bright wallpapers). At 0.72 alpha secondary fails on white
    // wallpapers; primary is the safe choice.
    val secondary = if (wallpaperEnabled) colors.textPrimary else colors.textSecondary
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
    music: MusicState?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    val isPlaying = music is MusicState.Playing
    // Only render metadata Android legitimately exposes via the media session.
    val title = (music as? MusicState.Playing)?.title ?: (music as? MusicState.Paused)?.title
    val label = title?.takeIf { it.isNotBlank() } ?: if (isPlaying) "Playing" else "Paused"
    val artist = (music as? MusicState.Playing)?.artist ?: (music as? MusicState.Paused)?.artist
    val appLabel = (music as? MusicState.Playing)?.appLabel ?: (music as? MusicState.Paused)?.appLabel
    val artworkBytes = (music as? MusicState.Playing)?.artwork?.bytes
        ?: (music as? MusicState.Paused)?.artwork?.bytes
    val canNext = (music as? MusicState.Playing)?.canNext ?: (music as? MusicState.Paused)?.canNext ?: true
    val canPrev = (music as? MusicState.Playing)?.canPrev ?: (music as? MusicState.Paused)?.canPrev ?: true

    // Decode the small, already-downsampled artwork bytes once per change (no main-thread churn).
    val imageBitmap = remember(artworkBytes) {
        artworkBytes?.let { bytes ->
            try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() } catch (_: Exception) { null }
        }
    }

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(AuraTheme.radius.small))
            .background(colors.surfaceRaised)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(AuraTheme.radius.small))
            .padding(AuraTheme.spacing.componentPadding)
            .semantics(mergeDescendants = true) { contentDescription = "Music: $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceBase),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Text(if (isPlaying) "♪" else "𝄞", style = typography.caption)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = typography.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artist != null) {
                Text(artist, style = typography.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (appLabel != null) {
                Text(appLabel, style = typography.caption.copy(fontSize = typography.caption.fontSize * 0.85f),
                    color = colors.textSecondary.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (canPrev) AuraChip(variant = ChipVariant.Secondary("◀"), onClick = onPrev)
        // Button reflects ACTUAL state: pause while playing, play while paused.
        AuraChip(variant = ChipVariant.Action(if (isPlaying) "❚❚" else "▶"), onClick = onPlayPause)
        if (canNext) AuraChip(variant = ChipVariant.Secondary("▶"), onClick = onNext)
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

/**
 * ONE contextual surface. Renders a single card for the current [ContextualItem].
 * When multiple items are relevant it rotates between them (subtle, auto, stops when
 * reduced motion or a single item) and shows pagination dots. Never three separate boxes.
 */
@Composable
private fun ContextualSurface(
    items: List<ContextualItem>,
    reducedMotion: Boolean,
    onMusicPlayPause: () -> Unit,
    onMusicNext: () -> Unit,
    onMusicPrev: () -> Unit,
    onRequestNextEventPermission: () -> Unit
) {
    if (items.isEmpty()) return
    var index by remember(items) { mutableStateOf(0) }
    // Keep the index in range when the set of relevant items shrinks.
    LaunchedEffect(items.size) { if (index >= items.size) index = 0 }

    // Subtle auto-rotation ONLY while visible, multiple items, and motion allowed.
    // The coroutine is cancelled when Home leaves foreground or the surface disappears,
    // and the guard prevents it from running at all when only one item remains.
    if (items.size > 1 && !reducedMotion) {
        LaunchedEffect(items.size) {
            while (true) {
                delay(6000)
                index = (index + 1) % items.size
            }
        }
    }

    val spec: AnimatedContentTransitionScope<Int>.() -> ContentTransform =
        if (reducedMotion) {
            { ContentTransform(EnterTransition.None, ExitTransition.None) }
        } else {
            {
                slideInHorizontally { it / 4 } + fadeIn(tween(200)) togetherWith
                    slideOutHorizontally { -it / 4 } + fadeOut(tween(200))
            }
        }

    Column(Modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = index,
            transitionSpec = spec,
            label = "contextual-surface"
        ) { i ->
            ContextualCard(
                item = items[i],
                onMusicPlayPause = onMusicPlayPause,
                onMusicNext = onMusicNext,
                onMusicPrev = onMusicPrev,
                onRequestNextEventPermission = onRequestNextEventPermission
            )
        }
        if (items.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.indices.forEach { dot ->
                    val selected = dot == index
                    Box(
                        Modifier
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) AuraTheme.colors.textPrimary
                                else AuraTheme.colors.textSecondary.copy(alpha = 0.4f)
                            )
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Contextual item ${dot + 1}",
                                onClick = { index = dot }
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
    }
}

@Composable
private fun ContextualCard(
    item: ContextualItem,
    onMusicPlayPause: () -> Unit,
    onMusicNext: () -> Unit,
    onMusicPrev: () -> Unit,
    onRequestNextEventPermission: () -> Unit
) {
    when (item) {
        is CalendarContextualItem -> NextEventRow(
            event = item.event, denied = item.denied, onRequestPermission = onRequestNextEventPermission
        )
        is BatteryContextualItem -> BatteryRow(battery = BatteryUiModel(item.percent, item.charging))
        is MusicContextualItem -> MusicRow(
            music = item.state, onPlayPause = onMusicPlayPause, onNext = onMusicNext, onPrev = onMusicPrev
        )
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
                    Modifier.size(56.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = { onLaunch(item.packageName) })
                        .auraFocusRing(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Icon fills the circle edge-to-edge — no inset background.
                    // Was 24dp inside 56dp (tiny); now 56dp fills the circle as users expect.
                    AppIcon(
                        packageName = item.packageName,
                        label = label,
                        contentDescription = label,
                        modifier = Modifier.clip(CircleShape),
                        iconSize = 54.dp
                    )
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
private fun WallpaperBackground(modifier: Modifier = Modifier) {
    // Stub — wallpaper is now the Window behind the activity (FLAG_SHOW_WALLPAPER).
    // Kept for layout stability; actual drawing is window-level, not Compose.
    Box(modifier)
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
