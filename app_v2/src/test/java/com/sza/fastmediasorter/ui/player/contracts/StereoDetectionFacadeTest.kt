package com.sza.fastmediasorter.ui.player.contracts

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.StereoDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StereoDetectionFacadeTest {

    private lateinit var facade: StereoDetectionFacade

    @Before
    fun setUp() {
        facade = StereoDetectionFacadeImpl(StereoDetector())
    }

    @Test
    fun `detectFromDimensions returns EQUIRECT_360_MONO for 4096x2048 media file`() {
        val mediaFile = buildVideoFile(width = 4096, height = 2048)

        assertEquals(StereoMode.EQUIRECT_360_MONO, facade.detectFromDimensions(mediaFile))
    }

    @Test
    fun `isStereoContent returns true for spherical mono because it still needs VR playback`() {
        val mediaFile = buildVideoFile(width = 4096, height = 2048)

        assertTrue(facade.isStereoContent(mediaFile))
    }

    @Test
    fun `isStereoContent returns true for spherical stereo`() {
        val mediaFile = buildVideoFile(width = 7680, height = 1920)

        assertTrue(facade.isStereoContent(mediaFile))
    }

    @Test
    fun `isStereoContent returns false for flat mono 16 by 9 content`() {
        val mediaFile = buildVideoFile(width = 1920, height = 1080)

        assertFalse(facade.isStereoContent(mediaFile))
    }

    @Test
    fun `detectFromDimensions returns UNKNOWN when dimensions are missing`() {
        val mediaFile = buildVideoFile(width = null, height = null)

        assertEquals(StereoMode.UNKNOWN, facade.detectFromDimensions(mediaFile))
        assertFalse(facade.isStereoContent(mediaFile))
    }

    private fun buildVideoFile(width: Int?, height: Int?): MediaFile = MediaFile(
        name = "sample.mp4",
        path = "/storage/emulated/0/Movies/sample.mp4",
        type = MediaType.VIDEO,
        size = 1L,
        createdDate = 0L,
        width = width,
        height = height,
    )
}