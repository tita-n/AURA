package com.aura.platform.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.aura.home.IconPixelTransform

/**
 * Monochromatic application-icon pipeline.
 *
 * AURA shows app icons in a single coherent tone while acting as the Home launcher,
 * for visual calm — without requiring users to install an icon pack.
 *
 * Pipeline:
 *   raw icon → native monochrome layer (API 33+) if present
 *           → else AURA fallback transformation (pixel luminance → alpha, tinted)
 *           → in-memory cache by package
 *
 * Design rules (see PRODUCT.md):
 *  - Never mutate the source Drawable.
 *  - Preserve alpha/transparency; avoid muddy low-contrast output.
 *  - No disk writes; in-memory cache only.
 *  - No expensive reprocessing on the UI thread — callers rasterize off-main-thread.
 */
object IconProcessor {

    /** In-memory cache contract. Injected so it can be faked in tests. */
    interface IconCache {
        fun get(key: String): Drawable?
        fun put(key: String, drawable: Drawable)
        fun invalidate(key: String)
    }

    /** Default LRU-backed cache. Keys by package name only (icons are static per package). */
    class MemoryIconCache(private val maxEntries: Int = 120) : IconCache {
        private val store = object : LruCache<String, Drawable>(maxEntries) {}
        override fun get(key: String): Drawable? = store.get(key)
        override fun put(key: String, drawable: Drawable) { store.put(key, drawable) }
        override fun invalidate(key: String) { store.remove(key) }
    }

    /** Shared default cache instance. */
    val defaultCache: IconCache = MemoryIconCache()

    /**
     * Produce a monochromatic [Drawable] for [packageName].
     * @param tint AURA tone (e.g. textPrimary) as ARGB int.
     */
    fun process(
        context: Context,
        packageName: String,
        source: Drawable,
        tint: Int,
        cache: IconCache = defaultCache
    ): Drawable {
        cache.get(packageName)?.let { return it }

        val mono: Drawable? = if (Build.VERSION.SDK_INT >= 33 && source is AdaptiveIconDrawable) {
            // Native monochrome layer available — tint it to AURA's tone for coherence.
            source.monochrome?.let { layer ->
                try {
                    layer.mutate().apply { setTint(tint) }
                } catch (_: Exception) { layer }
            }
        } else null

        val result = mono ?: fallbackMono(context, source, tint)
        cache.put(packageName, result)
        return result
    }

    /** Fallback: rasterize the source, transform luminance→alpha, tint, return a drawable. */
    private fun fallbackMono(context: Context, source: Drawable, tint: Int): Drawable {
        val size = 96
        val bmp = try {
            source.toBitmap(width = size, height = size)
        } catch (_: Exception) {
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        }
        val px = IntArray(size * size)
        bmp.getPixels(px, 0, size, 0, 0, size, size)
        val out = IconPixelTransform.monochrome(px, tint)
        val outBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        outBmp.setPixels(out, 0, size, 0, 0, size, size)
        return BitmapDrawable(context.resources, outBmp)
    }
}
