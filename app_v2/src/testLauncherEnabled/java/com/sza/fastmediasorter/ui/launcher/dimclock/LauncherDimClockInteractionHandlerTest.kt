package com.sza.fastmediasorter.ui.launcher.dimclock

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.provider.AlarmClock
import androidx.test.core.app.ApplicationProvider
import com.sza.fastmediasorter.ui.launcher.gadget.ClockGadgetStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * S3366 phase 03: the dim clock block replays the widget's gesture semantics on the shared state
 * store - the same seconds flag, dial color and typeface the desktop widget reads.
 */
@Suppress("FunctionNaming") // backtick test names, project convention
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherDimClockInteractionHandlerTest {

    private lateinit var application: Application
    private lateinit var store: ClockGadgetStateStore
    private lateinit var handler: LauncherDimClockInteractionHandler

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        store = ClockGadgetStateStore(application)
        store.setSecondsVisible(true)
        store.setDialColor(null)
        handler = LauncherDimClockInteractionHandler(application, store)
    }

    @Test
    fun `a right fling hides the seconds`() {
        val handled = handler.onFling(FLING_X, 0f, FLING_VELOCITY, 0f, TOUCH_SLOP, MIN_FLING_VELOCITY)

        assertTrue(handled)
        assertFalse(store.read().secondsVisible)
    }

    @Test
    fun `a left fling shows the seconds`() {
        store.setSecondsVisible(false)

        val handled = handler.onFling(-FLING_X, 0f, -FLING_VELOCITY, 0f, TOUCH_SLOP, MIN_FLING_VELOCITY)

        assertTrue(handled)
        assertTrue(store.read().secondsVisible)
    }

    @Test
    fun `an up fling randomizes the dial color and changes the typeface`() {
        val typefaceBefore = store.read().dialTypefaceName

        val handled = handler.onFling(0f, -FLING_X, 0f, -FLING_VELOCITY, TOUCH_SLOP, MIN_FLING_VELOCITY)

        assertTrue(handled)
        assertTrue(store.read().dialColor != null)
        assertNotEquals(typefaceBefore, store.read().dialTypefaceName)
    }

    @Test
    fun `a down fling resets the dial color`() {
        store.setDialColor(Color.RED)

        val handled = handler.onFling(0f, FLING_X, 0f, FLING_VELOCITY, TOUCH_SLOP, MIN_FLING_VELOCITY)

        assertTrue(handled)
        assertNull(store.read().dialColor)
    }

    @Test
    fun `a gesture under the thresholds is declined`() {
        val handled = handler.onFling(SMALL_X, 0f, SMALL_X, 0f, TOUCH_SLOP, MIN_FLING_VELOCITY)

        assertFalse(handled)
    }

    @Test
    fun `the tap opens the system clock app and dismisses dim before the start`() {
        shadowOf(application.packageManager).addResolveInfoForIntent(
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "com.android.deskclock"
                    name = "com.android.deskclock.DeskClock"
                }
            },
        )
        var dimDismissed = false

        val handled = handler.onClockTap { dimDismissed = true }

        assertTrue(handled)
        assertTrue(dimDismissed)
        assertEquals(
            AlarmClock.ACTION_SHOW_ALARMS,
            shadowOf(application).nextStartedActivity.action,
        )
    }

    @Test
    fun `the long press reports handled with no calendar app present`() {
        val handled = handler.onClockLongPress { }

        assertTrue(handled)
    }

    private companion object {
        const val FLING_X = 200f
        const val SMALL_X = 4f
        const val FLING_VELOCITY = 6000f
        const val TOUCH_SLOP = 32f
        const val MIN_FLING_VELOCITY = 50f
    }
}
