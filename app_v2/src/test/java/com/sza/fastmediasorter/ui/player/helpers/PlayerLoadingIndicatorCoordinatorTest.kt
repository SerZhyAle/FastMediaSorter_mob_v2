package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.view.isVisible
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * S0704: unit coverage for the source-counted spinner owner. Uses a real [View] attached to a
 * Robolectric [Activity] window (the coordinator's `sync()` is gated on `isAttachedToWindow`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerLoadingIndicatorCoordinatorTest {

    private lateinit var progressBar: View
    private lateinit var coordinator: PlayerLoadingIndicatorCoordinator
    private var destroyed = false

    @Before
    fun setUp() {
        destroyed = false
        // setup() drives create -> start -> resume -> visible, attaching the decor to a window;
        // a content view added afterwards is attached too, so isAttachedToWindow == true.
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        progressBar = View(activity).apply { isVisible = false }
        activity.setContentView(progressBar)
        val handler = Handler(Looper.getMainLooper())
        coordinator = PlayerLoadingIndicatorCoordinator(progressBar, handler) { destroyed }
    }

    @Test
    fun `empty source set hides the bar`() {
        coordinator.show(LoadingSource.FILE_LIST)
        coordinator.hide(LoadingSource.FILE_LIST)
        assertFalse(progressBar.isVisible)
    }

    @Test
    fun `one active source shows the bar`() {
        coordinator.show(LoadingSource.IMAGE_GLIDE)
        assertTrue(progressBar.isVisible)
    }

    @Test
    fun `hiding one of two sources keeps the bar visible until the last is gone`() {
        coordinator.show(LoadingSource.FILE_LIST)
        coordinator.show(LoadingSource.IMAGE_GLIDE)
        assertTrue(progressBar.isVisible)

        coordinator.hide(LoadingSource.FILE_LIST)
        assertTrue(progressBar.isVisible) // IMAGE_GLIDE still active

        coordinator.hide(LoadingSource.IMAGE_GLIDE)
        assertFalse(progressBar.isVisible)
    }

    @Test
    fun `delayed show makes the bar visible only after the delay elapses`() {
        coordinator.showDelayed(LoadingSource.IMAGE_GLIDE, delayMs = 1000)
        assertFalse(progressBar.isVisible)
        shadowOf(Looper.getMainLooper()).idleFor(1100, TimeUnit.MILLISECONDS)
        assertTrue(progressBar.isVisible)
    }

    @Test
    fun `reset cancels a pending delayed show`() {
        coordinator.showDelayed(LoadingSource.IMAGE_GLIDE, delayMs = 1000)
        coordinator.reset(LoadingSource.IMAGE_GLIDE)
        shadowOf(Looper.getMainLooper()).idleFor(2000, TimeUnit.MILLISECONDS)
        assertFalse(progressBar.isVisible)
    }

    @Test
    fun `reset of one source leaves another source pending show intact`() {
        coordinator.showDelayed(LoadingSource.IMAGE_GLIDE, delayMs = 1000)
        coordinator.reset(LoadingSource.FILE_LIST) // unrelated source
        shadowOf(Looper.getMainLooper()).idleFor(1100, TimeUnit.MILLISECONDS)
        assertTrue(progressBar.isVisible) // IMAGE_GLIDE delayed show survived
    }

    @Test
    fun `safety timeout hides the bar after the timeout elapses`() {
        coordinator.show(LoadingSource.IMAGE_GLIDE)
        coordinator.armSafetyTimeout(LoadingSource.IMAGE_GLIDE, timeoutMs = 30_000)
        assertTrue(progressBar.isVisible)
        shadowOf(Looper.getMainLooper()).idleFor(31_000, TimeUnit.MILLISECONDS)
        assertFalse(progressBar.isVisible)
    }

    @Test
    fun `hide before the safety timeout fires disarms it`() {
        coordinator.show(LoadingSource.IMAGE_GLIDE)
        coordinator.armSafetyTimeout(LoadingSource.IMAGE_GLIDE, timeoutMs = 30_000)
        coordinator.hide(LoadingSource.IMAGE_GLIDE)
        coordinator.show(LoadingSource.FILE_LIST) // independent source kept active across the timeout
        shadowOf(Looper.getMainLooper()).idleFor(31_000, TimeUnit.MILLISECONDS)
        assertTrue(progressBar.isVisible) // FILE_LIST must not be hidden by IMAGE_GLIDE's stale safety
    }

    @Test
    fun `clearAll hides the bar and is idempotent`() {
        coordinator.show(LoadingSource.FILE_LIST)
        coordinator.show(LoadingSource.OCR)
        coordinator.clearAll()
        assertFalse(progressBar.isVisible)
        coordinator.clearAll() // second call must not throw
        assertFalse(progressBar.isVisible)
    }

    @Test
    fun `sync is skipped while destroyed`() {
        coordinator.show(LoadingSource.FILE_LIST)
        assertTrue(progressBar.isVisible)
        destroyed = true
        coordinator.hide(LoadingSource.FILE_LIST) // sync() must early-return, leaving the view as-is
        assertTrue(progressBar.isVisible)
    }
}
