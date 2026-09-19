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
        manager.updateTimeouts(TIMEOUT_SECONDS, ON_CHARGE_OFF)
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
        manager.updateTimeouts(TIMEOUT_SECONDS, ON_CHARGE_OFF)
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
        manager.updateTimeouts(0, ON_CHARGE_OFF)
        manager.onStart()

        idle(LONG_WAIT_SECONDS)

        assertEquals(0, screenOffCalls)
        manager.onDestroy()
    }

    @Test
    fun `losing window focus pauses the countdown and regaining it starts over`() {
        val manager = manager()
        manager.updateTimeouts(TIMEOUT_SECONDS, ON_CHARGE_OFF)
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
        manager.updateTimeouts(TIMEOUT_SECONDS, ON_CHARGE_OFF)
        manager.onStart()
        manager.onStop()

        idle(LONG_WAIT_SECONDS)

        assertEquals(0, screenOffCalls)
        manager.onDestroy()
    }

    // --- S3284: the on-charge timeout ---

    @Test
    fun `connecting the charger switches the countdown to the on-charge timeout`() {
        val manager = manager()
        manager.updateTimeouts(TIMEOUT_SECONDS, ON_CHARGE_SECONDS)
        manager.onStart()
        manager.onChargingChanged(true)

        idle(TIMEOUT_SECONDS.toLong())

        assertEquals(0, screenOffCalls)

        idle((ON_CHARGE_SECONDS - TIMEOUT_SECONDS).toLong())

        assertEquals(1, screenOffCalls)
        manager.onDestroy()
    }

    @Test
    fun `an on-charge timeout of zero never turns the screen off while charging`() {
        val manager = manager()
        manager.updateTimeouts(TIMEOUT_SECONDS, ON_CHARGE_OFF)
        manager.onStart()
        manager.onChargingChanged(true)

        idle(LONG_WAIT_SECONDS)

        assertEquals(0, screenOffCalls)
        manager.onDestroy()
    }

    @Test
    fun `disconnecting the charger returns the countdown to the battery timeout`() {
        val manager = manager()
        manager.updateTimeouts(TIMEOUT_SECONDS, ON_CHARGE_OFF)
        manager.onStart()
        manager.onChargingChanged(true)

        idle(LONG_WAIT_SECONDS)
        manager.onChargingChanged(false)
        idle(TIMEOUT_SECONDS.toLong())

        assertEquals(1, screenOffCalls)
        manager.onDestroy()
    }

    @Test
    fun `a new on-charge value takes effect while the charger is already connected`() {
        val manager = manager()
        manager.updateTimeouts(TIMEOUT_SECONDS, ON_CHARGE_OFF)
        manager.onStart()
        manager.onChargingChanged(true)

        idle(LONG_WAIT_SECONDS)
        manager.updateTimeouts(TIMEOUT_SECONDS, ON_CHARGE_SECONDS)
        idle(ON_CHARGE_SECONDS.toLong())

        assertEquals(1, screenOffCalls)
        manager.onDestroy()
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5
        const val LONG_WAIT_SECONDS = 60L

        /** The default: the desktop never blacks out on its own while the charger is attached. */
        const val ON_CHARGE_OFF = 0
        const val ON_CHARGE_SECONDS = 20
    }
}
