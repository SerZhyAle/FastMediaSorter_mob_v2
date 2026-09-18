package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import com.sza.fastmediasorter.ui.player.PlaybackControlPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S3239: the restore moved off the caller's thread, so the two orderings it created are pinned here -
 * the stored pair still reaches the gains, and a preset tapped while the read was in flight is not
 * overwritten by the value that was on disk before it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelBalanceControllerRestoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetController() = ChannelBalanceController.resetForTest()

    @After
    fun clearStoredGains() {
        prefs().edit().clear().apply()
        ChannelBalanceController.resetForTest()
    }

    private fun prefs() = context
        .getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, Context.MODE_PRIVATE)

    private fun storeGains(left: Float, right: Float) {
        prefs().edit()
            .putFloat(PlaybackControlPreferences.KEY_BALANCE_LEFT_GAIN, left)
            .putFloat(PlaybackControlPreferences.KEY_BALANCE_RIGHT_GAIN, right)
            .apply()
    }

    @Test
    fun `a stored pair reaches the gains when the load lands`() {
        storeGains(STORED_LEFT, STORED_RIGHT)

        ChannelBalanceController.loadStoredGains(context)

        assertEquals(STORED_LEFT, ChannelBalanceController.leftGain, TOLERANCE)
        assertEquals(STORED_RIGHT, ChannelBalanceController.rightGain, TOLERANCE)
    }

    @Test
    fun `a preset tapped before the load lands survives it`() {
        storeGains(STORED_LEFT, STORED_RIGHT)

        ChannelBalanceController.setBalance(TAPPED_LEFT, TAPPED_RIGHT)
        ChannelBalanceController.loadStoredGains(context)

        assertEquals(TAPPED_LEFT, ChannelBalanceController.leftGain, TOLERANCE)
        assertEquals(TAPPED_RIGHT, ChannelBalanceController.rightGain, TOLERANCE)
    }

    @Test
    fun `an empty store leaves both gains at unity`() {
        ChannelBalanceController.loadStoredGains(context)

        assertEquals(ChannelBalanceController.UNITY_GAIN, ChannelBalanceController.leftGain, TOLERANCE)
        assertEquals(ChannelBalanceController.UNITY_GAIN, ChannelBalanceController.rightGain, TOLERANCE)
    }
}

private const val STORED_LEFT = 0.25f
private const val STORED_RIGHT = 0.75f
private const val TAPPED_LEFT = 1f
private const val TAPPED_RIGHT = 0.1f
private const val TOLERANCE = 0.0001f
