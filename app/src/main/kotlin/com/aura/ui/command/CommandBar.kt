package com.aura.ui.command

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aura.design.AuraTheme
import com.aura.design.LocalReducedMotion
import com.aura.design.auraFocusRing

/**
 * CommandBar — AURA's primary interaction surface. OS control surface, not a search box.
 * Fixed 56dp height. When results appear, the SURROUNDING resolution surface expands upward
 * above the keyboard. The Bar itself does not become a giant field.
 * No magnifying-glass icon. No skeleton. No gradients/glass/glow.
 *
 * States: Idle, Focused empty, Typing, Resolved/ACT, Ambiguous/ASK, Error, Disabled
 * Disabled is visible at reduced contrast — never hidden.
 */
@Composable
fun CommandBar(
    query: String,
    onQueryChange: (String) -> Unit,
    focused: Boolean,
    onFocusedChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean = true,
    placeholder: String = "Search anything",
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors
    val typography = AuraTheme.typography

    // Idle: surface.base, no border, text.secondary placeholder
    // Focused: surface.raised + border.subtle hairline
    val bg = if (focused) colors.surfaceRaised else colors.surfaceBase
    val borderColor = if (focused) colors.borderSubtle else colors.surfaceBase
    val alpha = if (enabled) 1f else 0.45f
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(AuraTheme.radius.large))
            .background(bg.copy(alpha = if (focused) 1f else alpha))
            .border(1.dp, borderColor.copy(alpha = if (focused) 1f else 0f), RoundedCornerShape(AuraTheme.radius.large))
            .auraFocusRing(RoundedCornerShape(AuraTheme.radius.large))
            .semantics { contentDescription = "Command bar" }
            .padding(horizontal = if (focused) 20.dp else 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                enabled = enabled,
                singleLine = true,
                textStyle = typography.commandInput.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accentDynamic),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        // The placeholder is purely instructional decoration (teaching
                        // examples). It must NOT be read as user-entered text, and its
                        // rotation must not cause repeated TalkBack announcements, so it
                        // is hidden from the accessibility tree. The field itself keeps the
                        // "Command bar" description (see Row semantics above).
                        val placeholderModifier = Modifier.clearAndSetSemantics { }
                        if (LocalReducedMotion.current) {
                            Text(
                                text = placeholder,
                                style = typography.commandInput,
                                color = colors.textSecondary.copy(alpha = alpha),
                                modifier = placeholderModifier
                            )
                        } else {
                            Crossfade(
                                targetState = placeholder,
                                animationSpec = tween(durationMillis = 300),
                                modifier = placeholderModifier
                            ) { text ->
                                Text(
                                    text = text,
                                    style = typography.commandInput,
                                    color = colors.textSecondary.copy(alpha = alpha)
                                )
                            }
                        }
                    }
                    innerTextField()
                }
            )
        }

        // Clear button — appears only when text present, 24dp icon, no confirmation
        // 48dp touch target via IconButton (ensures 48dp)
        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            IconButton(
                onClick = onClear,
                enabled = enabled,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Clear",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
