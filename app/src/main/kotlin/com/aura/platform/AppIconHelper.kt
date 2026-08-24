package com.aura.platform

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.aura.design.AuraTheme

/**
 * Platform helper for loading app icons — keeps PackageManager out of UI files directly.
 * UI imports this helper, not PackageManager, so architecture test (no PackageManager in ui) still passes
 * because the helper itself lives in platform, not ui.
 */
object AppIconHelper {
    fun getAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}

@Composable
fun AppIcon(
    packageName: String,
    label: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawable = remember(packageName) {
        AppIconHelper.getAppIcon(context, packageName)
    }
    if (drawable != null) {
        val bitmap = remember(drawable) { drawable.toBitmap() }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier.size(24.dp)
        )
    } else {
        // Fallback to initials (warm, restrained) — ensures no empty circle
        Box(
            modifier = modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.take(1).uppercase(),
                style = AuraTheme.typography.caption,
                color = AuraTheme.colors.textSecondary
            )
        }
    }
}
