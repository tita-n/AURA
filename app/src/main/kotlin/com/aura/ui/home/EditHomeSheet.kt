package com.aura.ui.home

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.design.AuraTheme
import com.aura.design.auraFocusRing
import com.aura.home.*
import com.aura.platform.AppIcon
import com.aura.resolver.IndexedEntity
import com.aura.ui.components.AuraChip
import com.aura.ui.components.ChipVariant

/**
 * AURA-native Home edit surface — minimal, token-only.
 *
 * Main sheet covers appearance, modules, dock, and widgets.
 * DockPicker and WidgetPicker are second-level sheets that return to Main.
 */
@Composable
fun HomeEditOverlay(
    surface: EditSurface,
    settings: HomeSettings,
    onSettingsChange: (HomeSettings) -> Unit,
    appIndex: List<IndexedEntity>,
    widgetLabels: Map<Int, String>,
    installedWidgetProviders: List<AppWidgetProviderInfo>,
    onAddWidget: (AppWidgetProviderInfo) -> Unit,
    onChooseWallpaper: () -> Unit,
    onClose: () -> Unit,
    onOpenDockPicker: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    onBackToMain: () -> Unit
) {
    if (surface is EditSurface.Closed) return

    Box(Modifier.fillMaxSize()) {
        // Scrim — tap to close; behind the sheet in z-order
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(role = Role.Button, onClick = onClose)
        )

        // Bottom sheet
        val scroll = rememberScrollState()
        val maxH = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxH)
                .clip(RoundedCornerShape(topStart = AuraTheme.radius.large, topEnd = AuraTheme.radius.large))
                .background(AuraTheme.colors.surfaceRaised)
                .verticalScroll(scroll)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier.width(32.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AuraTheme.colors.borderSubtle)
                    .align(Alignment.CenterHorizontally)
            )

            when (surface) {
                is EditSurface.DockPicker -> {
                    BackHandler(onBack = onBackToMain)
                    DockPickerContent(
                        appIndex = appIndex,
                        dock = settings.dock,
                        onAdd = { pkg ->
                            val next = DockLogic.add(settings.dock, pkg)
                            if (next !== settings.dock) onSettingsChange(settings.copy(dock = next))
                            onBackToMain()
                        },
                        onBack = onBackToMain
                    )
                }
                is EditSurface.WidgetPicker -> {
                    BackHandler(onBack = onBackToMain)
                    WidgetPickerContent(providers = installedWidgetProviders, onPick = {
                        onAddWidget(it)
                        onBackToMain()
                    }, onBack = onBackToMain)
                }
                else -> {
                    BackHandler(onBack = onClose)
                    MainEditContent(
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        onOpenDockPicker = onOpenDockPicker,
                        onOpenWidgetPicker = onOpenWidgetPicker,
                        onChooseWallpaper = onChooseWallpaper,
                        widgetLabels = widgetLabels,
                        onClose = onClose
                    )
                }
            }
        }
    }
}

@Composable
private fun MainEditContent(
    settings: HomeSettings,
    onSettingsChange: (HomeSettings) -> Unit,
    onOpenDockPicker: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    onChooseWallpaper: () -> Unit,
    widgetLabels: Map<Int, String>,
    onClose: () -> Unit
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Edit Home", style = typography.title, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        AuraChip(variant = ChipVariant.Secondary("Done"), onClick = onClose)
    }

    // ——— Appearance ———
    SectionHeader("Appearance")
    // Theme
    Text("Theme", style = typography.caption, color = colors.textSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ThemeMode.System to "System", ThemeMode.Dark to "Dark", ThemeMode.Light to "Light").forEach { (mode, label) ->
            val isSelected = settings.customization.themeMode == mode
            AuraChip(
                variant = if (isSelected) ChipVariant.Action(label) else ChipVariant.Secondary(label),
                onClick = { onSettingsChange(settings.copy(customization = settings.customization.copy(themeMode = mode))) }
            )
        }
    }
    // Accent
    Text("Accent", style = typography.caption, color = colors.textSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        // Dynamic option as a chip
        AuraChip(
            variant = if (settings.customization.accent is AccentChoice.Dynamic) ChipVariant.Action("Dynamic") else ChipVariant.Secondary("Dynamic"),
            onClick = { onSettingsChange(settings.copy(customization = settings.customization.copy(accent = AccentChoice.Dynamic))) }
        )
        AccentPalette.entries.forEachIndexed { idx, pair ->
            val isSelected = (settings.customization.accent as? AccentChoice.Curated)?.index == idx
            val dark = colors.isDark
            val argb = if (dark) pair.first else pair.second
            val c = Color(argb)
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(c)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) colors.textPrimary else colors.borderSubtle,
                        shape = CircleShape
                    )
                    .clickable(role = Role.Button, onClick = {
                        onSettingsChange(settings.copy(customization = settings.customization.copy(accent = AccentChoice.Curated(idx))))
                    })
                    .auraFocusRing(CircleShape)
                    .semantics { contentDescription = "Accent $idx${if (isSelected) ", selected" else ""}" }
            )
        }
    }
    // Animation
    Text("Motion", style = typography.caption, color = colors.textSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val s = settings.customization.animationIntensity == AnimationIntensity.Standard
        AuraChip(
            variant = if (s) ChipVariant.Action("Standard") else ChipVariant.Secondary("Standard"),
            onClick = { onSettingsChange(settings.copy(customization = settings.customization.copy(animationIntensity = AnimationIntensity.Standard))) }
        )
        AuraChip(
            variant = if (!s) ChipVariant.Action("Reduced") else ChipVariant.Secondary("Reduced"),
            onClick = { onSettingsChange(settings.copy(customization = settings.customization.copy(animationIntensity = AnimationIntensity.Reduced))) }
        )
    }
    // Wallpaper
    Text("Wallpaper", style = typography.caption, color = colors.textSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AuraChip(variant = ChipVariant.Secondary("Choose…"), onClick = onChooseWallpaper)
        val show = settings.customization.showWallpaper
        AuraChip(
            variant = if (show) ChipVariant.Action("Show behind Home") else ChipVariant.Secondary("Show behind Home"),
            onClick = { onSettingsChange(settings.copy(customization = settings.customization.copy(showWallpaper = !show))) }
        )
    }
    Text(
        if (settings.customization.showWallpaper) "Wallpaper visible behind a flat scrim — primary text only so contrast holds."
        else "Off — Home uses token surfaces (default, calm).",
        style = typography.caption, color = colors.textSecondary.copy(alpha = 0.7f)
    )

    Divider()

    // ——— Modules ———
    SectionHeader("Modules")
    Text("Optional, removable. Default: none. Ordering only among enabled ones.", style = typography.caption, color = colors.textSecondary)
    // Enabled list with reorder + remove
    settings.modules.forEachIndexed { idx, mod ->
        ModuleEditRow(
            label = mod.displayName(),
            canUp = idx > 0, canDown = idx < settings.modules.lastIndex,
            onUp = {
                val next = ModuleLogic.shift(settings.modules, mod, -1)
                onSettingsChange(settings.copy(modules = next))
            },
            onDown = {
                val next = ModuleLogic.shift(settings.modules, mod, +1)
                onSettingsChange(settings.copy(modules = next))
            },
            onRemove = {
                onSettingsChange(settings.copy(modules = ModuleLogic.disable(settings.modules, mod)))
            }
        )
    }
    if (settings.modules.isEmpty()) {
        Text("No modules.", style = typography.caption, color = colors.textSecondary.copy(alpha = 0.7f))
    }
    // Add chips for disabled modules
    val disabled = HomeModuleType.values().filter { it !in settings.modules }
    if (disabled.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            disabled.forEach { mod ->
                AuraChip(variant = ChipVariant.Action("Add ${mod.displayName()}"), onClick = {
                    onSettingsChange(settings.copy(modules = ModuleLogic.enable(settings.modules, mod)))
                })
            }
        }
    }

    Divider()

    // ——— Dock ———
    SectionHeader("Dock — 0–5 apps")
    Text("Tap an app to launch. No duplicates. Edited here.", style = typography.caption, color = colors.textSecondary)
    settings.dock.forEachIndexed { idx, item ->
        val label = item.packageName.substringAfterLast(".")
        DockEditRow(
            label = label,
            packageName = item.packageName,
            canUp = idx > 0, canDown = idx < settings.dock.lastIndex,
            onUp = {
                val next = DockLogic.move(settings.dock, idx, idx - 1)
                onSettingsChange(settings.copy(dock = next))
            },
            onDown = {
                val next = DockLogic.move(settings.dock, idx, idx + 1)
                onSettingsChange(settings.copy(dock = next))
            },
            onRemove = {
                onSettingsChange(settings.copy(dock = DockLogic.remove(settings.dock, item.packageName)))
            }
        )
    }
    if (settings.dock.isEmpty()) Text("Dock empty.", style = typography.caption, color = colors.textSecondary.copy(alpha = 0.7f))
    if (settings.dock.size < DockLogic.MAX) {
        AuraChip(variant = ChipVariant.Action("Add app to dock"), onClick = onOpenDockPicker)
    } else {
        Text("Dock full (5 max).", style = typography.caption, color = colors.textSecondary)
    }

    Divider()

    // ——— Widgets ———
    SectionHeader("Widgets")
    Text("Third-party Android widgets — advanced, optional, never required.", style = typography.caption, color = colors.textSecondary)
    settings.widgetIds.forEachIndexed { idx, wid ->
        val label = widgetLabels[wid] ?: "Widget #$wid"
        WidgetEditRow(
            label = label,
            canUp = idx > 0, canDown = idx < settings.widgetIds.lastIndex,
            onUp = {
                val m = settings.widgetIds.toMutableList(); val v = m.removeAt(idx); m.add(idx - 1, v)
                onSettingsChange(settings.copy(widgetIds = m))
            },
            onDown = {
                val m = settings.widgetIds.toMutableList(); val v = m.removeAt(idx); m.add(idx + 1, v)
                onSettingsChange(settings.copy(widgetIds = m))
            },
            onRemove = {
                val next = settings.widgetIds.filterNot { it == wid }
                onSettingsChange(settings.copy(widgetIds = next))
            }
        )
    }
    if (settings.widgetIds.isEmpty()) Text("No widgets.", style = typography.caption, color = colors.textSecondary.copy(alpha = 0.7f))
    AuraChip(variant = ChipVariant.Action("Add widget"), onClick = onOpenWidgetPicker)
    Text("Resizing: widgets receive your Home's real size. Reshape is responsive — no drag handles in v0.", style = typography.caption, color = colors.textSecondary.copy(alpha = 0.6f))
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = AuraTheme.typography.title.copy(fontSize = AuraTheme.typography.caption.fontSize * 1.1f), color = AuraTheme.colors.textPrimary, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(AuraTheme.colors.borderSubtle))
}

@Composable
private fun ModuleEditRow(label: String, canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(AuraTheme.radius.small))
            .background(AuraTheme.colors.surfaceBase)
            .border(1.dp, AuraTheme.colors.borderSubtle, RoundedCornerShape(AuraTheme.radius.small))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = AuraTheme.typography.body, color = AuraTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        if (canUp) AuraChip(variant = ChipVariant.Secondary("↑"), onClick = onUp) else Box(Modifier.size(32.dp))
        if (canDown) AuraChip(variant = ChipVariant.Secondary("↓"), onClick = onDown) else Box(Modifier.size(32.dp))
        AuraChip(variant = ChipVariant.Secondary("Remove"), onClick = onRemove)
    }
}

@Composable
private fun DockEditRow(label: String, packageName: String, canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(AuraTheme.radius.small))
            .background(AuraTheme.colors.surfaceBase)
            .border(1.dp, AuraTheme.colors.borderSubtle, RoundedCornerShape(AuraTheme.radius.small))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppIcon(packageName = packageName, label = label, contentDescription = label, modifier = Modifier.size(32.dp).clip(CircleShape))
        Text(label, style = AuraTheme.typography.body, color = AuraTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (canUp) AuraChip(variant = ChipVariant.Secondary("↑"), onClick = onUp) else Box(Modifier.size(32.dp))
        if (canDown) AuraChip(variant = ChipVariant.Secondary("↓"), onClick = onDown) else Box(Modifier.size(32.dp))
        AuraChip(variant = ChipVariant.Secondary("Remove"), onClick = onRemove)
    }
}

@Composable
private fun WidgetEditRow(label: String, canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(AuraTheme.radius.small))
            .background(AuraTheme.colors.surfaceBase)
            .border(1.dp, AuraTheme.colors.borderSubtle, RoundedCornerShape(AuraTheme.radius.small))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = AuraTheme.typography.body, color = AuraTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (canUp) AuraChip(variant = ChipVariant.Secondary("↑"), onClick = onUp) else Box(Modifier.size(32.dp))
        if (canDown) AuraChip(variant = ChipVariant.Secondary("↓"), onClick = onDown) else Box(Modifier.size(32.dp))
        AuraChip(variant = ChipVariant.Secondary("Remove"), onClick = onRemove)
    }
}

private fun HomeModuleType.displayName(): String = when (this) {
    HomeModuleType.NextEvent -> "Next Event"
    HomeModuleType.Battery -> "Battery"
    HomeModuleType.Music -> "Music"
}

@Composable
private fun DockPickerContent(appIndex: List<IndexedEntity>, dock: List<DockItem>, onAdd: (String) -> Unit, onBack: () -> Unit) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    var q by remember { mutableStateOf("") }
    val dockPkgs = remember(dock) { dock.map { it.packageName }.toSet() }
    val sorted = remember(appIndex) {
        appIndex.filter { it.id.startsWith("app:") }.sortedBy { it.displayLabel.lowercase() }
    }
    val filtered = remember(sorted, q, dockPkgs) {
        val t = q.trim().lowercase()
        val candidates = sorted.filter { it.id.removePrefix("app:") !in dockPkgs }
        if (t.isEmpty()) candidates else candidates.filter { it.displayLabel.lowercase().contains(t) || it.id.lowercase().contains(t) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Add app to dock", style = typography.title, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        AuraChip(variant = ChipVariant.Secondary("Back"), onClick = onBack)
    }
    if (dock.size >= DockLogic.MAX) {
        Text("Dock is full (5 max). Remove one first.", style = typography.caption, color = colors.textSecondary)
        return
    }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(AuraTheme.radius.small))
            .background(colors.surfaceBase).border(1.dp, colors.borderSubtle, RoundedCornerShape(AuraTheme.radius.small))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = q, onValueChange = { q = it },
                singleLine = true,
                textStyle = typography.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accentDynamic),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Filter apps" },
                decorationBox = { inner -> if (q.isEmpty()) Text("Filter apps…", style = typography.body, color = colors.textSecondary) else inner() }
            )
            if (q.isNotEmpty()) AuraChip(variant = ChipVariant.Secondary("Clear"), onClick = { q = "" })
        }
    }
    if (filtered.isEmpty()) {
        Text("No matches.", style = typography.caption, color = colors.textSecondary)
    } else {
        // LazyColumn inside a height-capped box — dock picker can be large
        Box(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { entity ->
                    val pkg = entity.id.removePrefix("app:")
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(AuraTheme.radius.small))
                            .background(colors.surfaceBase).border(1.dp, colors.borderSubtle, RoundedCornerShape(AuraTheme.radius.small))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppIcon(packageName = pkg, label = entity.displayLabel, contentDescription = entity.displayLabel, modifier = Modifier.size(32.dp).clip(CircleShape))
                        Text(entity.displayLabel, style = typography.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        AuraChip(variant = ChipVariant.Action("Add"), onClick = { onAdd(pkg) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPickerContent(providers: List<AppWidgetProviderInfo>, onPick: (AppWidgetProviderInfo) -> Unit, onBack: () -> Unit) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Add widget", style = typography.title, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        AuraChip(variant = ChipVariant.Secondary("Back"), onClick = onBack)
    }
    Text("Android widgets — no marketplace, no restyling.", style = typography.caption, color = colors.textSecondary)
    if (providers.isEmpty()) {
        Text("No home-screen widgets installed.", style = typography.body, color = colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
        return
    }
    Box(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(providers, key = { it.provider.flattenToString() }) { info ->
                val ctx = LocalContext.current
                val label = try { info.loadLabel(ctx.packageManager) } catch (_: Exception) { info.provider.packageName }
                val dims = "${info.minWidth}×${info.minHeight} dp"
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(AuraTheme.radius.small))
                        .background(colors.surfaceBase).border(1.dp, colors.borderSubtle, RoundedCornerShape(AuraTheme.radius.small))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceRaised), contentAlignment = Alignment.Center) {
                        Text("◧", style = typography.caption, color = colors.textSecondary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(label, style = typography.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(dims, style = typography.caption, color = colors.textSecondary)
                    }
                    AuraChip(variant = ChipVariant.Action("Add"), onClick = { onPick(info) })
                }
            }
        }
    }
}
