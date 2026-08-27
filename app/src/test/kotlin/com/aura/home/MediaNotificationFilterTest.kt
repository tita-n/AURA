package com.aura.home

import com.aura.TestPaths
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * The narrow listener must aggressively discard everything that is not demonstrably
 * media-related. Acceptance is decided on a content-free [MediaNotificationSignal],
 * never on the raw notification — so the original notification object cannot escape
 * the platform layer into the pure model.
 */
class MediaNotificationFilterTest {

    private fun signal(
        packageName: String? = "com.example.player",
        category: String? = null,
        hasMediaSession: Boolean = false,
        isMediaStyle: Boolean = false,
        isOngoing: Boolean = false
    ) = MediaNotificationSignal(packageName, category, hasMediaSession, isMediaStyle, isOngoing)

    @Test fun `media notification with session is accepted`() {
        assertTrue(MediaNotificationFilter.accept(signal(hasMediaSession = true)))
    }

    @Test fun `media-style notification is accepted`() {
        assertTrue(MediaNotificationFilter.accept(signal(isMediaStyle = true)))
    }

    @Test fun `transport category is accepted`() {
        assertTrue(MediaNotificationFilter.accept(signal(category = CAT_TRANSPORT)))
    }

    @Test fun `ongoing transport-like progress is accepted`() {
        assertTrue(MediaNotificationFilter.accept(signal(isOngoing = true, category = CAT_PROGRESS)))
    }

    @Test fun `ordinary message notification is rejected`() {
        assertFalse(MediaNotificationFilter.accept(signal(category = CAT_MESSAGE)))
    }

    @Test fun `email notification is rejected`() {
        assertFalse(MediaNotificationFilter.accept(signal(category = CAT_EMAIL)))
    }

    @Test fun `social notification is rejected`() {
        assertFalse(MediaNotificationFilter.accept(signal(category = CAT_SOCIAL)))
    }

    @Test fun `plain ongoing notification with no media is rejected`() {
        assertFalse(MediaNotificationFilter.accept(signal(isOngoing = true, category = null)))
    }

    @Test fun `unknown category with no media is rejected`() {
        assertFalse(MediaNotificationFilter.accept(signal(category = "somethingelse")))
    }

    @Test fun `original notification object does not escape the pure layer`() {
        // The pure model must be Android-free, so a StatusBarNotification can never enter it.
        val file = TestPaths.find("app/src/main/kotlin/com/aura/home/MediaContext.kt")
        assertTrue(file.exists())
        val text = file.readText()
        assertFalse("Pure media model must not import android.*", text.contains("import android"))
        assertFalse("Pure media model must not reference StatusBarNotification", text.contains("StatusBarNotification"))
    }

}
