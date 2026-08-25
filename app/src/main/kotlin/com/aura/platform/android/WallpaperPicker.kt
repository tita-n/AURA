package com.aura.platform.android

import android.content.Context
import android.content.Intent

/**
 * Wallpaper picker — system-owned capability, intent factory only.
 * Rendering behind Home is intentionally gated by [com.aura.home.HomeCustomization.showWallpaper];
 * when true a flat scrim (surfaceBase at ~94% alpha) preserves AA contrast against any wallpaper.
 */
object WallpaperPicker {

    fun intent(context: Context): Intent? {
        // Direct set-wallpaper entry (works universally); fall back to chooser only if preferred entry
        // is unavailable on OEMs that alias differently.
        val direct = Intent(Intent.ACTION_SET_WALLPAPER)
        if (direct.resolveActivity(context.packageManager) != null) return direct

        val chooser = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_SET_WALLPAPER))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (chooser.resolveActivity(context.packageManager) != null) chooser else null
    }
}
