package com.sza.fastmediasorter.ui.launcher.helpers

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherIdleScreenOffManagerTest {

    private var screenOffCalls = 0

    private fun manager(): LauncherIdleScreenOffManager =
        LauncherIdleScreenOffManager { screenOffCalls++ }

    private fun idle(seconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    }

    @Test
    fun `screen off is requested once when the countdown elapses`() {
        val manager = manager()
        manager.updateTimeout(TIMEOUT_SECONDS)
        manager.onStart()

        idle(TIMEOUT_SECONDS.toLong())

        assertEquals(1, screenOffCalls)

        idle(TIMEOUT_SECONDS.toLong())

        assertEquals(1, screenOffCalls)
        manager.onDestroy()
    }

    @Test
    fun `user input postpones the countdown`() {
        val manager = manager()
        manager.updateTimeout(TIMEOUT_SECONDS)
        manager.onStart()

        idle(TIMEOUT_SECONDS - 1L)
        manager.onUserInput()
        idle(TIMEOUT_SECONDS - 1L)

        assertEquals(0, screenOffCalls)

        idle(1L)

        assertEquals(1, screenOffCalls)
        manager.onDestroy()
    }

    @Test
    fun `timeout of zero never turns the screen off`() {
        val manager = manager()
        manager.updateTimeout(0)
        manager.onStart()

        idle(LONG_WAIT_SECONDS)

        assertEquals(0, screenOffCalls)
        manager.onDestroy()
    }

    @Test
    fun `losing window focus pauses the countdown and regaining it starts over`() {
        val manager = manager()
        manager.updateTimeout(TIMEOUT_SECONDS)
        manager.onStart()

        manager.onWindowFocusChanged(false)
        idle(LONG_WAIT_SECONDS)

        assertEquals(0, screenOffCalls)

        manager.onWindowFocusChanged(true)
        idle(TIMEOUT_SECONDS - 1L)

        assertEquals(0, screenOffCalls)

        idle(1L)

        assertEquals(1, screenOffCalls)
        manager.onDestroy()
    }

    @Test
    fun `stopping the activity cancels the countdown`() {
        val manager = manager()
        manager.updateTimeout(TIMEOUT_SECONDS)
        manager.onStart()
        manager.onStop()

        idle(LONG_WAIT_SECONDS)

        assertEquals(0, screenOffCalls)
        manager.onDestroy()
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5
        const val LONG_WAIT_SECONDS = 60L
    }
}
