package com.sza.fastmediasorter.ui.settings

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsActivityStateTest {

    @Test
    fun resolveInitialTabPosition_prefersScheduledDeepLinkOverSavedTab() {
        val intent = Intent().apply {
            putExtra(SettingsActivity.EXTRA_OPEN_SCHEDULED, true)
        }

        val position = SettingsActivity.resolveInitialTabPosition(
            intent = intent,
            adapterItemCount = 4,
            lastTabPosition = 1,
            sourceResourceId = -1L,
            initialTab = -1,
            enableScheduledOperations = true,
        )

        assertEquals(3, position)
    }

    @Test
    fun resolveInitialTabPosition_fallsBackToLastSavedTab() {
        val intent = Intent()

        val position = SettingsActivity.resolveInitialTabPosition(
            intent = intent,
            adapterItemCount = 4,
            lastTabPosition = 2,
            sourceResourceId = -1L,
            initialTab = -1,
            enableScheduledOperations = true,
        )

        assertEquals(2, position)
    }
}
