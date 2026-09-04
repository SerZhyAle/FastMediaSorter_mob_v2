package com.sza.fastmediasorter.ui.launcher.helpers

import android.app.Activity
import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.ref.WeakReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherScreenBlackoutManagerTest {

    @Test
    fun `blackout overlay shows and first touch dismisses and consumes event`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val manager = LauncherScreenBlackoutManager(WeakReference(activity))

        manager.updateTimeout(5)
        manager.onStart()

        assertFalse(manager.isOverlayVisible)

        manager.showBlackout()
        assertTrue(manager.isOverlayVisible)

        val downEvent = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        val consumed = manager.onDispatchTouchEvent(downEvent)
        downEvent.recycle()

        assertTrue(consumed)
        assertFalse(manager.isOverlayVisible)

        val nextDownEvent = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        val nextConsumed = manager.onDispatchTouchEvent(nextDownEvent)
        nextDownEvent.recycle()

        assertFalse(nextConsumed)
        manager.onDestroy()
    }

    @Test
    fun `blackout overlay dismissed and consumes key down and following key up`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val manager = LauncherScreenBlackoutManager(WeakReference(activity))

        manager.updateTimeout(10)
        manager.onStart()
        manager.showBlackout()
        assertTrue(manager.isOverlayVisible)

        val keyDownEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        val downConsumed = manager.onDispatchKeyEvent(keyDownEvent)

        assertTrue(downConsumed)
        assertFalse(manager.isOverlayVisible)

        val keyUpEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        val upConsumed = manager.onDispatchKeyEvent(keyUpEvent)
        assertTrue(upConsumed)

        val nextKeyDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        val nextConsumed = manager.onDispatchKeyEvent(nextKeyDown)
        assertFalse(nextConsumed)

        manager.onDestroy()
    }

    @Test
    fun `onStop dismisses blackout overlay`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val manager = LauncherScreenBlackoutManager(WeakReference(activity))

        manager.updateTimeout(10)
        manager.onStart()
        manager.showBlackout()
        assertTrue(manager.isOverlayVisible)

        manager.onStop()
        assertFalse(manager.isOverlayVisible)

        manager.onDestroy()
    }

    @Test
    fun `timeout 0 disables overlay and timer`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val manager = LauncherScreenBlackoutManager(WeakReference(activity))

        manager.updateTimeout(0)
        manager.onStart()
        manager.showBlackout()

        assertFalse(manager.isOverlayVisible)
        manager.onDestroy()
    }
}
