package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.data.local.LocalMediaScanner
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
        allFiles: Boolean = false,
        path: String = LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS
    ) = MediaResource(
        name = "Camera Photos",
        path = path,
        type = ResourceType.LOCAL,
        supportedMediaTypes = types,
        allFiles = allFiles
    )

    private fun useCase(capabilities: MediaCapabilities = allCapabilities) =
        ResolveScanFilterUseCase(capabilities)

    private fun mediaTypesFor(path: String, settings: AppSettings): Set<MediaType> =
        useCase()(resource(types = emptySet(), path = path), settings).mediaTypes

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
    fun `aggregate virtual resource follows live settings instead of stale snapshot`() {
        val settings = AppSettings(supportImages = true, supportVideos = false, supportGifs = false)

        val filter = useCase()(resource(types = setOf(MediaType.VIDEO)), settings)

        assertEquals(setOf(MediaType.IMAGE), filter.mediaTypes)
    }

    @Test
    fun `ordinary resource still intersects its stale snapshot with settings`() {
        val settings = AppSettings(supportImages = true, supportVideos = false, supportGifs = false)

        val filter = useCase()(
            resource(
                types = setOf(MediaType.VIDEO),
                path = "/storage/emulated/0/Pictures"
            ),
            settings
        )

        assertTrue(filter.mediaTypes.isEmpty())
    }

    @Test
    fun `all aggregate families derive their types from current settings`() {
        val settings = AppSettings()

        assertEquals(
            setOf(MediaType.AUDIO),
            mediaTypesFor(LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO, settings)
        )
        assertEquals(
            setOf(MediaType.VIDEO),
            mediaTypesFor(LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO, settings)
        )
        assertEquals(
            setOf(MediaType.IMAGE, MediaType.GIF),
            mediaTypesFor(LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES, settings)
        )
        assertEquals(
            setOf(MediaType.IMAGE, MediaType.GIF, MediaType.VIDEO),
            mediaTypesFor(LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS, settings)
        )
        assertEquals(
            setOf(MediaType.TEXT, MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT),
            mediaTypesFor(LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS, settings)
        )
    }

    @Test
    fun `allFiles resource ignores the snapshot and takes every type the flavor allows`() {
        val filter = useCase()(
            resource(
                types = setOf(MediaType.IMAGE),
                allFiles = true,
                path = LocalMediaScanner.VIRTUAL_PATH_RECENT
            ),
            AppSettings()
        )

        assertEquals(MediaType.entries.toSet(), filter.mediaTypes)
    }

    @Test
    fun `flavor gate removes types the build cannot open`() {
        val photosLike = allCapabilities.copy(supportsVideo = false, supportsAudio = false)
        val settings = AppSettings(allFiles = true)

        val filter = useCase(photosLike)(
            resource(allFiles = true, path = LocalMediaScanner.VIRTUAL_PATH_RECENT),
            settings
        )

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
