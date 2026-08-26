package com.aura.home

import org.junit.Assert.*
import org.junit.Test

/**
 * The monochrome transform is pure (operates on raw ARGB ints) and must stay that way.
 * Bright source pixels stay opaque, dark pixels recede (min 25% alpha so the silhouette
 * never fully vanishes), and transparent source stays transparent.
 */
class IconPixelTransformTest {

    // AURA dark-theme textPrimary tone: #FFF5F3EF
    private val tint = 0xFFF5F3EFL.toInt()

    @Test fun `transparent source stays transparent`() {
        val out = IconPixelTransform.monochrome(intArrayOf(0), tint)
        assertEquals(0, out[0])
    }

    @Test fun `opaque white keeps the tint color and full alpha`() {
        val out = IconPixelTransform.monochrome(intArrayOf(0xFFFFFFFFL.toInt()), tint)
        assertEquals(0xFFF5F3EFL.toInt(), out[0])
    }

    @Test fun `opaque black recedes but does not vanish`() {
        // lum 0 -> alpha = a * 0.25 = 63, rgb stays the tint
        val out = IconPixelTransform.monochrome(intArrayOf(0xFF000000L.toInt()), tint)
        val a = (out[0] shr 24) and 0xFF
        assertEquals(63, a)
        assertEquals(0xF5, (out[0] shr 16) and 0xFF)
        assertEquals(0xF3, (out[0] shr 8) and 0xFF)
        assertEquals(0xEF, out[0] and 0xFF)
    }

    @Test fun `bright source is more opaque than dark source`() {
        val white = (IconPixelTransform.monochrome(intArrayOf(0xFFFFFFFFL.toInt()), tint)[0] shr 24) and 0xFF
        val black = (IconPixelTransform.monochrome(intArrayOf(0xFF000000L.toInt()), tint)[0] shr 24) and 0xFF
        assertTrue("bright must dominate dark", white > black)
    }

    @Test fun `opaque red keeps tint rgb with luminance-modulated alpha`() {
        // lum(red) ~= 0.2126 -> alpha = 255 * (0.25 + 0.75*0.2126) ~= 104
        val out = IconPixelTransform.monochrome(intArrayOf(0xFFFF0000L.toInt()), tint)
        val a = (out[0] shr 24) and 0xFF
        assertTrue("red alpha should be ~104, was $a", a in 100..108)
        assertEquals(0xF5, (out[0] shr 16) and 0xFF)
        assertEquals(0xF3, (out[0] shr 8) and 0xFF)
        assertEquals(0xEF, out[0] and 0xFF)
    }

    @Test fun `output length matches input and is deterministic`() {
        val px = IntArray(25) { 0xFFFFFFFFL.toInt() }
        val out1 = IconPixelTransform.monochrome(px, tint)
        val out2 = IconPixelTransform.monochrome(px, tint)
        assertEquals(px.size, out1.size)
        assertArrayEquals(out1, out2)
    }
}
