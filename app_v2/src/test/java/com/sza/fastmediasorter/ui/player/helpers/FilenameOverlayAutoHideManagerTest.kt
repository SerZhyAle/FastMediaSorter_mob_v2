package com.sza.fastmediasorter.ui.player.helpers

import android.os.Handler
import android.widget.TextView
import com.sza.fastmediasorter.domain.model.MediaType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [FilenameOverlayAutoHideManager].
 *
 * Handler.postDelayed() is intercepted via MockK so no real timing is needed.
 * isFullscreen lambda is controlled by [fullscreenMode] to simulate panel transitions.
 *
 * NOTE: isVisible (KTX extension) is not verified directly because it compiles to
 * View.visibility which requires the Robolectric runner. Instead the tests assert
 * on postDelayed (confirming the show+timer path was taken) and alpha (cancel path).
 */
class FilenameOverlayAutoHideManagerTest {

    private lateinit var overlayView: TextView
    private lateinit var mainHandler: Handler
    private var fullscreenMode = false
    private lateinit var manager: FilenameOverlayAutoHideManager

    @Before
    fun setUp() {
        overlayView = mockk(relaxed = true)
        mainHandler = mockk(relaxed = true)

        // Stub alpha -- default 1f (visible) unless overridden per test
        every { overlayView.alpha } returns 1f
        every { mainHandler.postDelayed(any(), any<Long>()) } returns true

        manager = FilenameOverlayAutoHideManager(
            overlayView = overlayView,
            mainHandler = mainHandler,
            isFullscreen = { fullscreenMode }
        )
    }

    // Timeout mapping

    @Test
    fun `onFileShown uses 5s timeout for TEXT`() {
        fullscreenMode = false
        val delaySlot = slot<Long>()
        every { mainHandler.postDelayed(any(), capture(delaySlot)) } returns true

        manager.onFileShown(MediaType.TEXT)

        assertEquals("Expected 5000ms for TEXT", 5_000L, delaySlot.captured)
    }

    @Test
    fun `onFileShown uses 10s timeout for PDF`() {
        fullscreenMode = false
        val delaySlot = slot<Long>()
        every { mainHandler.postDelayed(any(), capture(delaySlot)) } returns true

        manager.onFileShown(MediaType.PDF)

        assertEquals("Expected 10000ms for PDF", 10_000L, delaySlot.captured)
    }

    @Test
    fun `onFileShown uses 10s timeout for EPUB`() {
        fullscreenMode = false
        val delaySlot = slot<Long>()
        every { mainHandler.postDelayed(any(), capture(delaySlot)) } returns true

        manager.onFileShown(MediaType.EPUB)

        assertEquals("Expected 10000ms for EPUB", 10_000L, delaySlot.captured)
    }

    @Test
    fun `onFileShown uses 15s timeout for VIDEO`() {
        fullscreenMode = false
        val delaySlot = slot<Long>()
        every { mainHandler.postDelayed(any(), capture(delaySlot)) } returns true

        manager.onFileShown(MediaType.VIDEO)

        assertEquals("Expected 15000ms for VIDEO", 15_000L, delaySlot.captured)
    }

    @Test
    fun `onFileShown uses 15s timeout for IMAGE`() {
        fullscreenMode = false
        val delaySlot = slot<Long>()
        every { mainHandler.postDelayed(any(), capture(delaySlot)) } returns true

        manager.onFileShown(MediaType.IMAGE)

        assertEquals("Expected 15000ms for IMAGE", 15_000L, delaySlot.captured)
    }

    @Test
    fun `onFileShown uses 15s timeout for GIF`() {
        fullscreenMode = false
        val delaySlot = slot<Long>()
        every { mainHandler.postDelayed(any(), capture(delaySlot)) } returns true

        manager.onFileShown(MediaType.GIF)

        assertEquals("Expected 15000ms for GIF", 15_000L, delaySlot.captured)
    }

    @Test
    fun `onFileShown uses 15s timeout for AUDIO`() {
        fullscreenMode = false
        val delaySlot = slot<Long>()
        every { mainHandler.postDelayed(any(), capture(delaySlot)) } returns true

        manager.onFileShown(MediaType.AUDIO)

        assertEquals("Expected 15000ms for AUDIO", 15_000L, delaySlot.captured)
    }

    // Fullscreen deferral

    @Test
    fun `onFileShown in fullscreen does not post delayed runnable`() {
        fullscreenMode = true
        manager.onFileShown(MediaType.IMAGE)

        verify(exactly = 0) { mainHandler.postDelayed(any(), any<Long>()) }
    }

    // Re-show when hidden -- confirmed by postDelayed being called (show path arms the timer)

    @Test
    fun `onPauseInteraction re-shows overlay when already hidden`() {
        fullscreenMode = false
        every { overlayView.alpha } returns 0f

        manager.onPauseInteraction(MediaType.VIDEO)

        // postDelayed was called -> re-show + new timer was armed
        verify(atLeast = 1) { mainHandler.postDelayed(any(), any<Long>()) }
    }

    @Test
    fun `onPauseInteraction extends deadline when overlay still visible`() {
        fullscreenMode = false
        every { overlayView.alpha } returns 1f

        manager.onFileShown(MediaType.VIDEO)
        manager.onPauseInteraction(MediaType.VIDEO)

        // postDelayed called twice: once for onFileShown, once for the extension
        verify(exactly = 2) { mainHandler.postDelayed(any(), any<Long>()) }
    }

    // Zoom interaction

    @Test
    fun `onZoomInteraction is ignored for non-image types`() {
        fullscreenMode = false
        every { overlayView.alpha } returns 0f

        manager.onZoomInteraction(MediaType.VIDEO)

        verify(exactly = 0) { mainHandler.postDelayed(any(), any<Long>()) }
    }

    @Test
    fun `onZoomInteraction re-shows overlay when hidden for IMAGE`() {
        fullscreenMode = false
        every { overlayView.alpha } returns 0f

        manager.onZoomInteraction(MediaType.IMAGE)

        verify(atLeast = 1) { mainHandler.postDelayed(any(), any<Long>()) }
    }

    @Test
    fun `onZoomInteraction re-shows overlay when hidden for GIF`() {
        fullscreenMode = false
        every { overlayView.alpha } returns 0f

        manager.onZoomInteraction(MediaType.GIF)

        verify(atLeast = 1) { mainHandler.postDelayed(any(), any<Long>()) }
    }

    // Fullscreen guards

    @Test
    fun `onPauseInteraction is ignored in fullscreen`() {
        fullscreenMode = true
        manager.onPauseInteraction(MediaType.VIDEO)

        verify(exactly = 0) { mainHandler.postDelayed(any(), any<Long>()) }
    }

    @Test
    fun `onZoomInteraction is ignored in fullscreen`() {
        fullscreenMode = true
        manager.onZoomInteraction(MediaType.IMAGE)

        verify(exactly = 0) { mainHandler.postDelayed(any(), any<Long>()) }
    }

    // Lifecycle pause / resume

    @Test
    fun `onHostPause removes pending callbacks`() {
        fullscreenMode = false
        manager.onFileShown(MediaType.IMAGE)
        manager.onHostPause()

        verify(atLeast = 2) { mainHandler.removeCallbacks(any()) }
    }

    @Test
    fun `onHostResume reschedules timer when overlay was logically visible`() {
        fullscreenMode = false
        manager.onFileShown(MediaType.IMAGE)
        manager.onHostPause()
        manager.onHostResume(MediaType.IMAGE)

        // postDelayed called at least twice: onFileShown + resume
        verify(atLeast = 2) { mainHandler.postDelayed(any(), any<Long>()) }
    }

    @Test
    fun `onHostResume does not reschedule when overlay was not shown`() {
        fullscreenMode = false
        // Call resume without ever calling onFileShown -- overlay is never logically visible
        manager.onHostResume(MediaType.IMAGE)

        verify(exactly = 0) { mainHandler.postDelayed(any(), any<Long>()) }
    }

    // Cancel -- alpha is a plain Float property, safe to verify on JVM

    @Test
    fun `cancel removes callbacks and sets alpha to 0`() {
        fullscreenMode = false
        manager.onFileShown(MediaType.IMAGE)
        manager.cancel()

        verify(atLeast = 2) { mainHandler.removeCallbacks(any()) }
        verify { overlayView.alpha = 0f }
    }
}
