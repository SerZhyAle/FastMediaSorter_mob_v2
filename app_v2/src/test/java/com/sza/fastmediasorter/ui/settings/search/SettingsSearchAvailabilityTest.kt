package com.sza.fastmediasorter.ui.settings.search

import com.sza.fastmediasorter.ui.settings.SettingsSearchDestination
import com.sza.fastmediasorter.ui.settings.SettingsSearchIndex
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchAvailabilityTest {

    private val deviceFeatureGate = mockk<SettingsSearchDeviceFeatureGate>()

    private val availability = SettingsSearchAvailability(
        supportedMedia = setOf("images", "video", "audio", "documents"),
        deviceFeatureGate = deviceFeatureGate
    )

    @Test
    fun `drops pip row when sdk does not support it`() {
        every { deviceFeatureGate.isAvailable("rowEnablePip") } returns false

        assertFalse(availability.isAvailable(entry(key = "rowEnablePip", sectionId = "playback")))
    }

    @Test
    fun `drops accelerometer gated rows when sensor is missing`() {
        every { deviceFeatureGate.isAvailable("rowFollowSystemRotation") } returns false
        every { deviceFeatureGate.isAvailable("rowFollowSystemRotationPlayer") } returns false

        assertFalse(availability.isAvailable(entry(key = "rowFollowSystemRotation", sectionId = "other")))
        assertFalse(availability.isAvailable(entry(key = "rowFollowSystemRotationPlayer", sectionId = "playback")))
    }

    @Test
    fun `keeps unrelated rows available when device gate passes`() {
        every { deviceFeatureGate.isAvailable("rowShowBlackScreenButton") } returns true

        assertTrue(availability.isAvailable(entry(key = "rowShowBlackScreenButton", sectionId = "playback")))
    }

    private fun entry(key: String, sectionId: String) = SettingsSearchIndex(
        key = key,
        title = key,
        keywords = listOf(key),
        sectionId = sectionId,
        destination = SettingsSearchDestination.PLAYBACK,
        viewId = 1
    )
}
