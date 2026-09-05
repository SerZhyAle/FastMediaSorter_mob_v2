package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import com.sza.fastmediasorter.testutil.testMediaCapabilities
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyEnableAllSettingsUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val routeAvailability = mockk<ResolvePanelRouteAvailabilityUseCase>()

    private fun caps(
        video: Boolean = true,
        audio: Boolean = true,
        images: Boolean = true,
        documents: Boolean = true,
        epub: Boolean = true,
        cloud: Boolean = true,
        defaultPlayer: Boolean = true,
    ) = testMediaCapabilities(
        supportsVideo = video,
        supportsAudio = audio,
        supportsImages = images,
        supportsDocuments = documents,
        supportsEpub = epub,
        supportsCloud = cloud,
        supportsDefaultPlayer = defaultPlayer,
    )

    // S0876: the use case now writes through the transform overload (mutex-serialized in the real
    // repository) - capture the transform lambda and apply it to `start` ourselves to get the result.
    // S2382: [compiledRoutes] stands in for the route resolver's build half; every route is compiled
    // unless a test narrows it, which is what a flavor carrying fewer features looks like here.
    private fun run(
        start: AppSettings,
        capabilities: MediaCapabilities,
        compiledRoutes: Set<String> = allRouteKeys,
    ): AppSettings {
        coEvery { routeAvailability.all() } returns allRouteKeys.associateWith { key ->
            ResolvePanelRouteAvailabilityUseCase.Availability(
                availableInBuild = key in compiledRoutes,
                enabledAtRuntime = false,
            )
        }
        val transform = slot<suspend (AppSettings) -> AppSettings>()
        coEvery { settingsRepository.updateSettings(capture(transform)) } returns Unit
        runTest {
            ApplyEnableAllSettingsUseCase(settingsRepository, capabilities, routeAvailability)()
        }
        coVerify { settingsRepository.updateSettings(any<suspend (AppSettings) -> AppSettings>()) }
        return runBlocking { transform.captured(start) }
    }

    @Test
    fun `all capabilities force every whitelisted flag on`() {
        val result = run(AppSettings(), caps())
        assertTrue(result.allFiles)
        assertTrue(result.supportAudio)
        assertTrue(result.supportVideos)
        assertTrue(result.supportText)
        assertTrue(result.supportPdf)
        assertTrue(result.supportOfficeDocuments)
        assertTrue(result.supportEpub)
        assertTrue(result.enablePersistentAudioPlayback)
        assertTrue(result.acceptSharedFiles)
        assertTrue(result.isPrimaryMediaPlayer)
    }

    @Test
    fun `no audio capability leaves audio flags untouched`() {
        val start = AppSettings(supportAudio = false, enablePersistentAudioPlayback = false)
        val result = run(start, caps(audio = false))
        assertFalse(result.supportAudio)
        assertFalse(result.enablePersistentAudioPlayback)
        assertTrue(result.allFiles)
        assertTrue(result.isPrimaryMediaPlayer)
    }

    @Test
    fun `no documents capability leaves document flags untouched`() {
        val start = AppSettings(
            supportText = false,
            supportPdf = false,
            supportEpub = false,
            supportOfficeDocuments = false,
        )
        val result = run(start, caps(documents = false, epub = false))
        assertFalse(result.supportText)
        assertFalse(result.supportPdf)
        assertFalse(result.supportEpub)
        assertFalse(result.supportOfficeDocuments)
        assertTrue(result.allFiles)
    }

    // S2382: images and GIFs were the two media-type siblings the whitelist skipped, and the `images`
    // capability parameter above existed with nothing asserting on it - the gap sat exactly here.
    @Test
    fun `images capability restores image support that the user switched off`() {
        val start = AppSettings(supportImages = false, supportGifs = false)
        val result = run(start, caps())
        assertTrue(result.supportImages)
        assertTrue(result.supportGifs)
    }

    @Test
    fun `no images capability leaves image support untouched`() {
        val start = AppSettings(supportImages = false)
        val result = run(start, caps(images = false))
        assertFalse(result.supportImages)
        // GIF support carries no capability field of its own, so it is not gated by one.
        assertTrue(result.supportGifs)
    }

    @Test
    fun `every compiled route has its runtime switch turned on`() {
        val result = run(AppSettings(), caps())
        assertTrue(result.enableCalculator)
        assertTrue(result.enableNetworkMonitor)
        assertTrue(result.embeddedGameEnabled)
        assertTrue(result.enableSystemInfo)
        assertTrue(result.enableWearCompanion)
        assertTrue(result.micRecordingEnabled)
        assertTrue(result.screenRecordingEnabled)
        assertTrue(result.frontFlashlightEnabled)
        assertTrue(result.enableFavorites)
        assertTrue(result.linkAutoDownloadEnabled)
        assertTrue(result.enableScheduledOperations)
        assertFalse(result.disableCameraCapture)
        assertFalse(result.disableVideoCapture)
    }

    @Test
    fun `a route absent from this build leaves its switch alone`() {
        val compiled = allRouteKeys - InternalRouteCatalog.KEY_QUICK_VOICE -
            InternalRouteCatalog.KEY_SCREEN_RECORDING
        val result = run(AppSettings(), caps(), compiledRoutes = compiled)
        assertFalse(result.micRecordingEnabled)
        assertFalse(result.screenRecordingEnabled)
        assertTrue(result.enableCalculator)
    }

    // S2382: the settings the membership rule keeps out - privacy, destructive defaults, network cost,
    // a switch that needs a resource the user has not picked, and one owning its own consent page.
    @Test
    fun `settings outside the membership rule are never touched`() {
        val result = run(AppSettings(), caps())
        assertFalse(result.recordGnssTrack)
        assertFalse(result.cameraGeotagEnabled)
        assertFalse(result.overwriteOnCopy)
        assertFalse(result.overwriteOnMove)
        assertFalse(result.useTrash)
        assertFalse(result.enableBackgroundSync)
        assertFalse(result.enableThumbnailPreload)
        assertFalse(result.showHiddenFiles)
        assertFalse(result.enablePhotosDuringAudio)
        assertFalse(result.gestureOverlayEnabled)
    }

    private companion object {
        val allRouteKeys: Set<String> = InternalRouteCatalog.all().mapTo(mutableSetOf()) { it.key }
    }
}
