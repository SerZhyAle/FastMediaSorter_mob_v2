package com.sza.fastmediasorter.ui.player.helpers

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoInteractionManagersTest {
    private val context = RuntimeEnvironment.getApplication()
    private val surface = View(context).apply { layout(0, 0, 400, 300) }
    private val player = mockk<Player>(relaxed = true)
    private val playerView = mockk<PlayerView>(relaxed = true).also {
        every { it.context } returns context
        every { it.videoSurfaceView } returns surface
        every { it.visibility } returns View.VISIBLE
        every { it.player } returns player
        every { it.width } returns 400
        every { it.height } returns 300
        every { it.isControllerFullyVisible } returns true
        every { player.videoSize } returns VideoSize(400, 300)
    }

    @Test
    fun `multitouch tail cannot become a legacy tap`() {
        val manager = VideoZoomGestureManager(playerView)
        manager.onPreDraw()
        event(MotionEvent.ACTION_DOWN).useEvent { assertFalse(manager.handleTouchEvent(it)) }
        multiEvent().useEvent { assertTrue(manager.handleTouchEvent(it)) }
        event(MotionEvent.ACTION_MOVE).useEvent { assertTrue(manager.handleTouchEvent(it)) }
        event(MotionEvent.ACTION_UP).useEvent { assertTrue(manager.handleTouchEvent(it)) }
        event(MotionEvent.ACTION_DOWN).useEvent { assertFalse(manager.handleTouchEvent(it)) }
        verify(exactly = 0) { player.seekTo(any<Long>()) }
        manager.release()
    }

    @Test
    fun `hidden controls are revealed before a tap can trigger navigation`() {
        every { playerView.isControllerFullyVisible } returns false
        val manager = VideoZoomGestureManager(playerView)
        manager.onPreDraw()
        event(MotionEvent.ACTION_DOWN).useEvent { assertFalse(manager.handleTouchEvent(it)) }
        event(MotionEvent.ACTION_UP).useEvent { assertTrue(manager.handleTouchEvent(it)) }
        verify(exactly = 1) { playerView.showController() }
        manager.release()
    }

    @Test
    fun `mouse zoom only transforms the surface and a new file resets it`() {
        val mouseListener = slot<View.OnGenericMotionListener>()
        every { playerView.setOnGenericMotionListener(capture(mouseListener)) } answers { }
        var item = MediaItem.fromUri("file:///first.mp4")
        every { player.currentMediaItem } answers { item }
        val manager = VideoZoomGestureManager(playerView)
        manager.onPreDraw()
        val coordinates = MotionEvent.PointerCoords().apply { setAxisValue(MotionEvent.AXIS_VSCROLL, 1f) }
        val properties = MotionEvent.PointerProperties().apply { id = 0 }
        MotionEvent.obtain(
            0, 10, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties), arrayOf(coordinates),
            KeyEvent.META_CTRL_ON, 0, 1f, 1f, 0, 0, 0, 0
        ).useEvent { assertTrue(mouseListener.captured.onGenericMotion(playerView, it)) }
        assertEquals(1.25f, surface.scaleX, 0f)
        verify(exactly = 0) { playerView.setScaleX(any()) }
        item = MediaItem.fromUri("file:///second.mp4")
        manager.onPreDraw()
        assertEquals(1f, surface.scaleX, 0f)
        assertEquals(0f, surface.translationX, 0f)
        manager.release()
    }

    @Test
    fun `chrome restores original disabled buttons without changing fullscreen visibility`() {
        every { player.isPlaying } returns true
        every { playerView.isControllerFullyVisible } returns false
        val enabled = Button(context)
        val disabled = Button(context).apply { isEnabled = false }
        val panel = LinearLayout(context).apply {
            addView(enabled)
            addView(disabled)
        }
        val manager = VideoChromeVisibilityManager(playerView, panel)
        manager.onPreDraw()
        assertEquals(View.VISIBLE, panel.visibility)
        assertEquals(0f, panel.alpha, 0f)
        assertFalse(enabled.isEnabled)
        every { player.isPlaying } returns false
        manager.onPreDraw()
        assertEquals(1f, panel.alpha, 0f)
        assertTrue(enabled.isEnabled)
        assertFalse(disabled.isEnabled)
        manager.release()
    }

    private fun event(action: Int): MotionEvent = MotionEvent.obtain(0, 100, action, 100f, 100f, 0)

    private fun multiEvent(): MotionEvent {
        val properties = Array(2) { index -> MotionEvent.PointerProperties().apply { id = index } }
        val coordinates = Array(2) { index ->
            MotionEvent.PointerCoords().apply {
                x = 100f + index * 100f
                y = 100f
            }
        }
        return MotionEvent.obtain(
            0, 50, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
    }

    private fun MotionEvent.useEvent(block: (MotionEvent) -> Unit) {
        try { block(this) } finally { recycle() }
    }
}
