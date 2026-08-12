package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1584: the card counter and the Browse list used to derive the scan filter independently, and the
 * counter's derivation was a superset on both axes - so a card could promise 45 files against an
 * empty list. These tests pin the single derivation both call sites now share.
 */
class ResolveScanFilterUseCaseTest {

    private val allCapabilities = MediaCapabilities(
        supportsVideo = true,
        supportsAudio = true,
        supportsImages = true,
        supportsDocuments = true,
        supportsEpub = true,
        supportsCloud = true,
        supportsLocalNetworkSources = true,
        supportsDefaultPlayer = true,
        supportsCast = true,
        supportsMicRecording = true,
        supportsVrPlayer = true,
        supportsWearCompanion = true
    )

    private fun resource(
        types: Set<MediaType> = setOf(MediaType.IMAGE),
        allFiles: Boolean = false
    ) = MediaResource(
        name = "Camera Photos",
        path = "virtual://camera_photos",
        type = ResourceType.LOCAL,
        supportedMediaTypes = types,
        allFiles = allFiles
    )

    private fun useCase(capabilities: MediaCapabilities = allCapabilities) =
        ResolveScanFilterUseCase(capabilities)

    @Test
    fun `carries the user size ceiling instead of an unbounded one`() {
        val settings = AppSettings(imageSizeMax = TEN_MEGABYTES)

        val filter = useCase()(resource(), settings)

        assertEquals(TEN_MEGABYTES, filter.sizeFilter.imageSizeMax)
        assertEquals(settings.videoSizeMax, filter.sizeFilter.videoSizeMax)
        assertEquals(settings.audioSizeMax, filter.sizeFilter.audioSizeMax)
    }

    @Test
    fun `intersects the resource snapshot with the globally enabled types`() {
        val settings = AppSettings(supportImages = true, supportVideos = false, supportGifs = false)

        val filter = useCase()(resource(types = setOf(MediaType.IMAGE, MediaType.VIDEO)), settings)

        assertEquals(setOf(MediaType.IMAGE), filter.mediaTypes)
    }

    @Test
    fun `yields no types when the resource snapshot went stale against the settings`() {
        val settings = AppSettings(supportImages = true, supportVideos = false, supportGifs = false)

        val filter = useCase()(resource(types = setOf(MediaType.VIDEO)), settings)

        assertTrue(filter.mediaTypes.isEmpty())
    }

    @Test
    fun `allFiles resource ignores the snapshot and takes every type the flavor allows`() {
        val filter = useCase()(resource(types = setOf(MediaType.IMAGE), allFiles = true), AppSettings())

        assertEquals(MediaType.entries.toSet(), filter.mediaTypes)
    }

    @Test
    fun `flavor gate removes types the build cannot open`() {
        val photosLike = allCapabilities.copy(supportsVideo = false, supportsAudio = false)
        val settings = AppSettings(allFiles = true)

        val filter = useCase(photosLike)(resource(allFiles = true), settings)

        assertTrue(MediaType.VIDEO !in filter.mediaTypes)
        assertTrue(MediaType.AUDIO !in filter.mediaTypes)
        assertTrue(MediaType.IMAGE in filter.mediaTypes)
    }

    @Test
    fun `withoutSizeCeiling lifts only the ceilings and keeps the type set`() {
        val base = useCase()(resource(), AppSettings(imageSizeMax = TEN_MEGABYTES))

        val lifted = useCase().withoutSizeCeiling(base)

        assertEquals(Long.MAX_VALUE, lifted.sizeFilter.imageSizeMax)
        assertEquals(base.sizeFilter.imageSizeMin, lifted.sizeFilter.imageSizeMin)
        assertEquals(base.mediaTypes, lifted.mediaTypes)
    }

    private companion object {
        const val TEN_MEGABYTES = 10_485_760L
    }
}
