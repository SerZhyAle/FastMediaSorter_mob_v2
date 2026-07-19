package com.sza.fastmediasorter.ui.settings.search

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.screencapture.MenuScreenshotLauncher
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.ui.settings.SettingsSearchDestination
import com.sza.fastmediasorter.ui.settings.SettingsSearchIndex
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchCapabilityGateTest {

    @Test
    fun `suppresses gesture-card row when controller set is empty`() {
        val gate = gate(controllers = emptySet())

        assertFalse(gate.isAvailable(entry(ancestorIds = listOf(R.id.groupScreenGestures))))
    }

    @Test
    fun `keeps gesture-card row when controller set is non-empty`() {
        val gate = gate(controllers = nonEmptyControllers())

        assertTrue(gate.isAvailable(entry(ancestorIds = listOf(R.id.groupScreenGestures))))
    }

    @Test
    fun `keeps row that has no gated ancestor regardless of capability sets`() {
        val gate = gate(controllers = emptySet())

        assertTrue(gate.isAvailable(entry(ancestorIds = listOf(R.id.containerBehaviour))))
    }

    @Test
    fun `keeps row with empty ancestor chain`() {
        val gate = gate(controllers = emptySet())

        assertTrue(gate.isAvailable(entry(ancestorIds = emptyList())))
    }

    @Test
    fun `suppresses mic-recording rows when mic capability absent`() {
        val gate = gate(media = mediaCaps(supportsMicRecording = false))

        assertFalse(gate.isAvailable(entry(key = "rowMicRecordingEnabled")))
        assertFalse(gate.isAvailable(entry(key = "rowMicRecordingAskFilename")))
        assertFalse(gate.isAvailable(entry(key = "btnSelectMicRecordingDest")))
    }

    @Test
    fun `keeps mic-recording rows when mic capability present`() {
        val gate = gate(media = mediaCaps(supportsMicRecording = true))

        assertTrue(gate.isAvailable(entry(key = "rowMicRecordingEnabled")))
    }

    @Test
    fun `suppresses background-audio rows including header when playback capability absent`() {
        val capability = capabilityAvailability { every { isPersistentAudioPlaybackAvailable() } returns false }
        val gate = gate(capability = capability)

        assertFalse(gate.isAvailable(entry(key = "rowEnablePersistentAudioPlayback")))
        assertFalse(gate.isAvailable(entry(key = "rowShowNowPlayingPanel")))
        assertFalse(gate.isAvailable(entry(key = "headerBackgroundAudio")))
    }

    @Test
    fun `keeps background-audio rows when playback capability present`() {
        val capability = capabilityAvailability { every { isPersistentAudioPlaybackAvailable() } returns true }
        val gate = gate(capability = capability)

        assertTrue(gate.isAvailable(entry(key = "rowEnablePersistentAudioPlayback")))
    }

    @Test
    fun `suppresses cloud source row when cloud capability absent`() {
        val gate = gate(media = mediaCaps(supportsCloud = false))

        assertFalse(gate.isAvailable(entry(key = "rowSourceCloud")))
    }

    @Test
    fun `keeps cloud source row when cloud capability present`() {
        val gate = gate(media = mediaCaps(supportsCloud = true))

        assertTrue(gate.isAvailable(entry(key = "rowSourceCloud")))
    }

    @Test
    fun `suppresses ocr and translation rows when translation capability absent`() {
        val capability = capabilityAvailability { every { isTranslationAvailable() } returns false }
        val gate = gate(capability = capability)

        assertFalse(gate.isAvailable(entry(key = "rowEnableTranslation")))
        assertFalse(gate.isAvailable(entry(key = "rowEnableOcr")))
        assertFalse(gate.isAvailable(entry(key = "rowTranslationLensStyle")))
        assertFalse(gate.isAvailable(entry(key = "rowCameraOcrTranslationEnabled")))
        assertFalse(gate.isAvailable(entry(key = "rowCameraOcrOnly")))
    }

    @Test
    fun `keeps ocr and translation rows when translation capability present`() {
        val capability = capabilityAvailability { every { isTranslationAvailable() } returns true }
        val gate = gate(capability = capability)

        assertTrue(gate.isAvailable(entry(key = "rowEnableTranslation")))
    }

    @Test
    fun `suppresses ocr spinner and language picker rows when translation capability absent`() {
        val capability = capabilityAvailability { every { isTranslationAvailable() } returns false }
        val gate = gate(capability = capability)

        assertFalse(gate.isAvailable(entry(key = "spinnerTranslationSourceLanguage")))
        assertFalse(gate.isAvailable(entry(key = "spinnerTranslationTargetLanguage")))
        assertFalse(gate.isAvailable(entry(key = "rowOcrEngineType")))
        assertFalse(gate.isAvailable(entry(key = "rowPaddleOcrModel")))
        assertFalse(gate.isAvailable(entry(key = "rowOcrFontSize")))
        assertFalse(gate.isAvailable(entry(key = "rowOcrFontFamily")))
    }

    @Test
    fun `keeps ocr spinner and language picker rows when translation capability present`() {
        val capability = capabilityAvailability { every { isTranslationAvailable() } returns true }
        val gate = gate(capability = capability)

        assertTrue(gate.isAvailable(entry(key = "spinnerTranslationSourceLanguage")))
        assertTrue(gate.isAvailable(entry(key = "rowOcrFontFamily")))
    }

    @Test
    fun `suppresses extensions button when extensions screen unavailable`() {
        val capability = capabilityAvailability { every { isExtensionsScreenAvailable() } returns false }
        val gate = gate(capability = capability)

        assertFalse(gate.isAvailable(entry(key = "btnDownloadableExtensions")))
    }

    @Test
    fun `keeps extensions button when extensions screen available`() {
        val capability = capabilityAvailability { every { isExtensionsScreenAvailable() } returns true }
        val gate = gate(capability = capability)

        assertTrue(gate.isAvailable(entry(key = "btnDownloadableExtensions")))
    }

    // S1051: relocated to the always-available System-apps group, so its per-row branch is now the
    // only gate on a genuinely-indexed search result - mirror the runtime isFallbackCaptureAvailable() gate.
    @Test
    fun `suppresses accessibility-shortcut button when no controller offers the silent path`() {
        assertFalse(gate(controllers = emptySet()).isAvailable(entry(key = "btnOpenAccessibilitySettings")))
        assertFalse(
            gate(controllers = controllersWithSilentPath(available = false))
                .isAvailable(entry(key = "btnOpenAccessibilitySettings"))
        )
    }

    @Test
    fun `keeps accessibility-shortcut button when a controller offers the silent path`() {
        val gate = gate(controllers = controllersWithSilentPath(available = true))

        assertTrue(gate.isAvailable(entry(key = "btnOpenAccessibilitySettings")))
    }

    // S1052: relocated into the General-tab debug section, losing the groupScreenGestures container
    // gate, so this per-row branch is now the only search gate. BuildConfig.DEBUG is true in the
    // unit-test variant, so these exercise the flavor (launcher-bound) axis of the composite gate.
    @Test
    fun `suppresses screenshot-test button when no menu-screenshot launcher is bound`() {
        assertFalse(gate(launchers = emptySet()).isAvailable(entry(key = "btnTakeScreenshotNow")))
    }

    @Test
    fun `keeps screenshot-test button when a menu-screenshot launcher is bound`() {
        assertTrue(gate(launchers = menuLaunchers()).isAvailable(entry(key = "btnTakeScreenshotNow")))
    }

    @Test
    fun `does not gate default-player keys - registry owns them`() {
        val gate = gate(media = mediaCaps(supportsDefaultPlayer = false))

        // The gate leaves these untouched (else -> true); SettingsSearchRegistry.isCapabilityAvailable owns them (S0602).
        assertTrue(gate.isAvailable(entry(key = "rowPrimaryMediaPlayer")))
        assertTrue(gate.isAvailable(entry(key = "btnSettingsDefaultPlayerImages")))
    }

    @Test
    fun `launcher rows are gated by the launcher capability`() {
        // S1088: the enable toggle + the launcher-settings entry row are the only launcher rows still in a
        // catalogued tab layout (General); the composition/density rows moved into the dialog (uncatalogued).
        assertTrue(gate(launcherAvailable = true).isAvailable(entry(key = "rowLauncherModeEnabled")))
        assertFalse(gate(launcherAvailable = false).isAvailable(entry(key = "rowLauncherSettings")))
    }

    private fun gate(
        controllers: Set<ScreenGestureOverlayController> = nonEmptyControllers(),
        launchers: Set<MenuScreenshotLauncher> = emptySet(),
        media: MediaCapabilities = mediaCaps(),
        capability: CapabilityAvailability = capabilityAvailability(),
        launcherAvailable: Boolean = true
    ) = SettingsSearchCapabilityGate(
        screenGestureControllers = controllers,
        menuScreenshotLaunchers = launchers,
        mediaCapabilities = media,
        capabilityAvailability = capability,
        launcherModeContract = mockk(relaxed = true) { every { isAvailableInBuild } returns launcherAvailable }
    )

    private fun nonEmptyControllers(): Set<ScreenGestureOverlayController> = setOf(mockk(relaxed = true))

    private fun menuLaunchers(): Set<MenuScreenshotLauncher> = setOf(mockk(relaxed = true))

    private fun controllersWithSilentPath(available: Boolean): Set<ScreenGestureOverlayController> =
        setOf(mockk(relaxed = true) { every { isFallbackCaptureAvailable() } returns available })

    private fun capabilityAvailability(
        stub: CapabilityAvailability.() -> Unit = {}
    ): CapabilityAvailability = mockk(relaxed = true) {
        every { isPersistentAudioPlaybackAvailable() } returns true
        every { isTranslationAvailable() } returns true
        every { isExtensionsScreenAvailable() } returns true
        stub()
    }

    private fun mediaCaps(
        supportsMicRecording: Boolean = true,
        supportsCloud: Boolean = true,
        supportsDefaultPlayer: Boolean = true
    ): MediaCapabilities = MediaCapabilities(
        supportsVideo = true,
        supportsAudio = true,
        supportsImages = true,
        supportsDocuments = true,
        supportsEpub = true,
        supportsCloud = supportsCloud,
        supportsLocalNetworkSources = true,
        supportsDefaultPlayer = supportsDefaultPlayer,
        supportsCast = true,
        supportsMicRecording = supportsMicRecording,
        supportsVrPlayer = false,
        supportsWearCompanion = true
    )

    private fun entry(
        key: String = "row",
        ancestorIds: List<Int> = emptyList()
    ) = SettingsSearchIndex(
        key = key,
        title = "row",
        keywords = listOf("row"),
        sectionId = "other",
        destination = SettingsSearchDestination.OPERATIONS,
        viewId = 1,
        ancestorIds = ancestorIds
    )
}
