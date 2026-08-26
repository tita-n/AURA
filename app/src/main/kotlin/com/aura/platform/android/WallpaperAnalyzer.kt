package com.aura.platform.android

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.aura.home.WallpaperTreatment
import com.aura.home.WallpaperTreatmentResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Estimates the system wallpaper's brightness and returns an adaptive treatment.
 *
 * Strategy (cheap, local-first, no extra permissions):
 *  - API 27+: uses [WallpaperManager.getWallpaperColors] (computed by the system, no bitmap
 *    decode, works for live wallpapers). We average the available primary/secondary/tertiary
 *    colors' luminance as a brightness estimate.
 *  - Below API 27, or when colors are unavailable: falls back to a conservative "bright"
 *    treatment (strong scrim) so text stays readable; the wallpaper is still shown.
 *
 * Caching: the last wallpaper id → treatment is cached so recompositions or repeated
 * calls do not re-run work. No polling — the caller refreshes on wallpaper change / resume.
 */
class WallpaperAnalyzer(private val context: Context) {

    private val wallpapers: WallpaperManager = WallpaperManager.getInstance(context)
    private var cachedId: Int? = null
    private var cachedTreatment: WallpaperTreatment? = null

    suspend fun analyze(): WallpaperTreatment = withContext(Dispatchers.IO) {
        val currentId = try {
            if (Build.VERSION.SDK_INT >= 24) wallpapers.getWallpaperId(WallpaperManager.FLAG_SYSTEM) else -1
        } catch (_: Exception) { -1 }

        if (cachedTreatment != null && cachedId == currentId) {
            return@withContext cachedTreatment!!
        }

        val treatment = if (Build.VERSION.SDK_INT >= 27) {
            try {
                val colors = wallpapers.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                val candidates = buildList {
                    colors?.primaryColor?.toArgb()?.let { add(Color(it)) }
                    colors?.secondaryColor?.toArgb()?.let { add(Color(it)) }
                    colors?.tertiaryColor?.toArgb()?.let { add(Color(it)) }
                }
                if (candidates.isNotEmpty()) {
                    val avgLum = candidates.map { WallpaperTreatmentResolver.luminanceOf(it) }.average().toFloat()
                    WallpaperTreatmentResolver.classify(avgLum)
                } else {
                    WallpaperTreatmentResolver.classify(null)
                }
            } catch (_: Exception) {
                WallpaperTreatmentResolver.classify(null)
            }
        } else {
            // Pre-API-27: no permission-free way to sample brightness; use conservative bright.
            WallpaperTreatmentResolver.classify(null)
        }

        cachedId = currentId
        cachedTreatment = treatment
        return@withContext treatment
    }

    fun clearCache() {
        cachedId = null
        cachedTreatment = null
    }
}
