package com.sza.fastmediasorter.ui.player.entry

import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PlayerEntryCoordinatorTest {

    private lateinit var coordinator: PlayerEntryCoordinator

    @Before
    fun setUp() {
        coordinator = PlayerEntryCoordinatorImpl()
    }

    @Test
    fun `standard flavor shows VR CTA for spherical mono video`() {
        val decision = coordinator.resolveEntry(
            PlaybackEntryRequest(
                flavorSupportsVr = false,
                currentDeviceClass = DeviceClass.PHONE,
                detectedStereoMode = StereoMode.EQUIRECT_360_MONO,
                mediaType = MediaType.VIDEO,
            )
        )

        assertEquals(PlaybackEntryDecision.ShowVrInstallCta, decision)
    }

    @Test
    fun `standard flavor shows VR CTA for spherical mono image`() {
        val decision = coordinator.resolveEntry(
            PlaybackEntryRequest(
                flavorSupportsVr = false,
                currentDeviceClass = DeviceClass.PHONE,
                detectedStereoMode = StereoMode.CYLINDER_180,
                mediaType = MediaType.IMAGE,
            )
        )

        assertEquals(PlaybackEntryDecision.ShowVrInstallCta, decision)
    }

    @Test
    fun `standard flavor keeps 2D video in standard player`() {
        val decision = coordinator.resolveEntry(
            PlaybackEntryRequest(
                flavorSupportsVr = false,
                currentDeviceClass = DeviceClass.PHONE,
                detectedStereoMode = StereoMode.MONO,
                mediaType = MediaType.VIDEO,
            )
        )

        assertEquals(PlaybackEntryDecision.OpenStandardPlayer, decision)
    }
}