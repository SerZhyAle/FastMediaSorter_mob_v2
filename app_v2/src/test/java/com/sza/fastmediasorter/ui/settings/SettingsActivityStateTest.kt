package com.sza.fastmediasorter.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S3365: the scheduled deep-link branch left [SettingsActivity.resolveInitialTabPosition] - stale
 * EXTRA_OPEN_SCHEDULED intents are redirected to the program screen in onCreate before any tab
 * resolution, so only the plain precedence rules remain to cover here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsActivityStateTest {

    @Test
    fun resolveInitialTabPosition_prefersExplicitTabOverSavedTab() {
        val position = SettingsActivity.resolveInitialTabPosition(
            adapterItemCount = 4,
            lastTabPosition = 2,
            initialTab = 1,
        )

        assertEquals(1, position)
    }

    @Test
    fun resolveInitialTabPosition_fallsBackToLastSavedTab() {
        val position = SettingsActivity.resolveInitialTabPosition(
            adapterItemCount = 4,
            lastTabPosition = 2,
            initialTab = -1,
        )

        assertEquals(2, position)
    }

    @Test
    fun resolveInitialTabPosition_defaultsToFirstTabWithNoHistory() {
        val position = SettingsActivity.resolveInitialTabPosition(
            adapterItemCount = 4,
            lastTabPosition = -1,
            initialTab = -1,
        )

        assertEquals(0, position)
    }
}
