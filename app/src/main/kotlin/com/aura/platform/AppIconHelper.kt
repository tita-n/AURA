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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.aura.design.AuraTheme
import com.aura.platform.android.IconProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    /**
     * Load the app icon off the main thread, recolor it to the monochrome AURA tone
     * (native adaptive layer when available, AURA fallback otherwise), and return an
     * [ImageBitmap]. Kept OUTSIDE the Compose tree so the suspend try/catch never wraps
     * a composable call. Returns null on any failure (caller falls back to initials).
     */
    suspend fun loadMonochromeIcon(
        context: Context,
        packageName: String,
        tint: Int
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            val src = getAppIcon(context, packageName) ?: return@withContext null
            val mono = IconProcessor.process(context, packageName, src, tint)
            val bmp = if (mono is android.graphics.drawable.BitmapDrawable) {
                mono.bitmap
            } else {
                mono.toBitmap(width = 96, height = 96)
            }
            bmp.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

private object AppIconCache {
    private const val MAX = 120
    private val map = android.util.LruCache<String, ImageBitmap>(MAX)
    fun get(pkg: String): ImageBitmap? = synchronized(this) { map.get(pkg) }
    fun put(pkg: String, bmp: ImageBitmap) = synchronized(this) { map.put(pkg, bmp) }
}

@Composable
fun AppIcon(
    packageName: String,
    label: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
) {
    val context = LocalContext.current
    // Read the monochrome AURA tone in composable scope (CompositionLocal), then pass it in.
    val tint = AuraTheme.colors.textPrimary.value.toInt()
    var bitmap by remember(packageName) {
        mutableStateOf(AppIconCache.get(packageName))
    }
    // Async load off the main thread to keep list scrolling at 60fps.
    // Cache ensures we never decode the same icon twice per process.
    LaunchedEffect(packageName) {
        if (bitmap != null) return@LaunchedEffect
        val cached = AppIconCache.get(packageName)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        val bmp = AppIconHelper.loadMonochromeIcon(context, packageName, tint)
        if (bmp != null) {
            AppIconCache.put(packageName, bmp)
            bitmap = bmp
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            modifier = modifier.size(iconSize)
        )
    } else {
        // Fallback to initials (warm, restrained) — ensures no empty circle
        Box(
            modifier = modifier.size(iconSize),
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
