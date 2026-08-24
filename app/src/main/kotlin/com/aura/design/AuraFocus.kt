package com.aura.design

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Visible focus indicator — Design Language §18, Task 2.
 * Must not rely solely on color, must remain visible in grayscale, must be distinct from
 * touch selection tint (which is background 0.12 accent). Focus is an *outline* (border),
 * selection is a *fill* — semantically distinct and grayscale-distinguishable.
 * Does not alter 48dp touch target — border is drawn inside the composable bounds with no size change.
 * Follows Aura radius/border/accent/spacing tokens — no new visual language.
 */
fun Modifier.auraFocusRing(
    shape: Shape = RoundedCornerShape(AuraRadius.small)
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { isFocused = it.isFocused }
        .then(
            if (isFocused) Modifier.border(2.dp, AuraColorTokens.Dark.borderSubtle, shape)
            else Modifier
        )
}
