package com.sza.fastmediasorter.ui.settings.helpers

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotGestureActionPickerManagerTest {

    @Test
    fun `broadcast action follows its enabled setting`() {
        assertFalse(
            ScreenshotGestureActionPickerManager.isEnabledInSettings(
                ScreenshotGestureAction.START_BROADCAST,
                AppSettings(enableBroadcasting = false),
            ),
        )
        assertTrue(
            ScreenshotGestureActionPickerManager.isEnabledInSettings(
                ScreenshotGestureAction.START_BROADCAST,
                AppSettings(enableBroadcasting = true),
            ),
        )
    }

    @Test
    fun `tourist action follows its enabled setting`() {
        assertFalse(
            ScreenshotGestureActionPickerManager.isEnabledInSettings(
                ScreenshotGestureAction.OPEN_TOURIST_INFO,
                AppSettings(enableTourist = false),
            ),
        )
        assertTrue(
            ScreenshotGestureActionPickerManager.isEnabledInSettings(
                ScreenshotGestureAction.OPEN_TOURIST_INFO,
                AppSettings(enableTourist = true),
            ),
        )
    }
}
