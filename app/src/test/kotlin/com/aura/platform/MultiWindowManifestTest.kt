package com.aura.platform

import com.aura.TestPaths
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiWindowManifestTest {
    @Test
    fun `launcher activity explicitly opts into resizeable windows`() {
        val manifest = TestPaths.find("app/src/main/AndroidManifest.xml").readText()
        val activity = manifest.substringAfter("android:name=\".MainActivity\"")
            .substringBefore("</activity>")
        assertTrue(
            "MainActivity must opt into Android resizeable/multi-window participation",
            activity.contains("android:resizeableActivity=\"true\"")
        )
    }
}
