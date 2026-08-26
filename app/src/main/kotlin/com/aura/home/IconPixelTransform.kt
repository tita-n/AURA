package com.aura.home

/**
 * Pure monochrome icon transform (no Android dependencies) so it stays unit-testable on the JVM.
 *
 * Design Direction §3.6 aesthetic: launcher icons are recolored to the single AURA tone and
 * treated as silhouettes. Brightness is preserved as alpha — light parts of the source become
 * more opaque, dark parts become transparent — so detail survives as a tonal mask rather than a
 * flat fill. No AI, no edge detection, no per-device tuning.
 *
 * Operates on a raw ARGB [IntArray] buffer so it can run without any Android types.
 */
object IconPixelTransform {

    /**
     * Turn an ARGB [pixels] buffer into a monochrome silhouette tinted with [tint] (also ARGB).
     * Transparent source pixels stay transparent; opaque pixels take [tint] with alpha modulated
     * by the source's perceptual luminance (bright shapes stay visible, dark shapes recede).
     */
    fun monochrome(pixels: IntArray, tint: Int): IntArray {
        val tr = (tint shr 16) and 0xFF
        val tg = (tint shr 8) and 0xFF
        val tb = tint and 0xFF
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            val a = (p shr 24) and 0xFF
            if (a == 0) return@IntArray 0 // fully transparent — stay transparent
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Perceptual luminance (0..1) of the source pixel.
            val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            // Modulate alpha by luminance so bright shapes stay visible, dark shapes
            // recede — this is what avoids a muddy solid blob and keeps the icon recognizable.
            val outA = (a * (0.25f + 0.75f * lum)).toInt().coerceIn(0, 255)
            (outA shl 24) or (tr shl 16) or (tg shl 8) or tb
        }
    }
}
