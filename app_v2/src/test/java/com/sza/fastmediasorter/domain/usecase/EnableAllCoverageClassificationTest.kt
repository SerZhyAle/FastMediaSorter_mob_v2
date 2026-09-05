package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import com.sza.fastmediasorter.testutil.testMediaCapabilities
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * S2382: the standing answer to "does the welcome Enable-all button still cover everything".
 *
 * Membership rule (also in [ApplyEnableAllSettingsUseCase]'s KDoc): a setting is switched on by the
 * button when doing so unlocks a capability this build already ships and the user has nothing further
 * to choose. Everything else is out, and [Coverage] names the reason it is out.
 *
 * Why a test and not a runtime table: nothing in production reads the classification, and an unused map
 * in `src/main` is dead weight (Rule 20). Why it exists at all: the button's field list was written by
 * hand and drifted away from the settings model unnoticed, until it covered 13 of 137 booleans while its
 * own KDoc claimed it covered every one of them. A new boolean now fails here until someone decides
 * which side it belongs on.
 */
class EnableAllCoverageClassificationTest {

    enum class Coverage {
        /** Switched on by [ApplyEnableAllSettingsUseCase] itself. */
        ENABLED_BY_BUTTON,

        /** Switched on by the welcome orchestrator, but only after its deliverable installs (S0386). */
        ENABLED_AFTER_INSTALL,

        /** Collection or protection: switching it on answers for the user in Play Data Safety. */
        PRIVACY_OR_SECURITY,

        /** Switching it on risks data the user already has. */
        DESTRUCTIVE_OR_DATA_RISK,

        /** Costs network or battery, and ships as an opt-in for that reason. */
        COST_OPT_IN,

        /** Inert until the user picks a resource, so the flag alone delivers nothing. */
        NEEDS_USER_RESOURCE,

        /** Owns a consent step of its own - a wizard page or a system special-access screen. */
        OWN_CONSENT_SURFACE,

        /**
         * Chooses how something looks or behaves rather than whether it exists; also covers stored UI
         * state, one-shot hint flags, and master switches already on by default.
         */
        PREFERENCE_MODE_OR_STATE,
    }

    @Test
    fun `every boolean setting carries a coverage decision`() {
        val declared = declaredBooleanSettingNames()
        assertEquals(
            "A settings boolean has no coverage decision. Classify it in this file: does the welcome " +
                "Enable-all button switch it on? The membership rule is in ApplyEnableAllSettingsUseCase's KDoc.",
            emptySet<String>(),
            declared - classified,
        )
        assertEquals(
            "A classified name is no longer a boolean in AppSettings - drop it from this file.",
            emptySet<String>(),
            classified - declared,
        )
    }

    @Test
    fun `the button changes only settings classified as enabled by it`() {
        val before = AppSettings()
        val after = applyEnableAll(before)
        val changed = declaredBooleanSettingNames().filterTo(mutableSetOf()) { name ->
            booleanValue(before, name) != booleanValue(after, name)
        }
        assertEquals(
            "Enable-all changed a setting that is not classified ENABLED_BY_BUTTON.",
            emptySet<String>(),
            changed - CLASSIFICATION.getValue(Coverage.ENABLED_BY_BUTTON),
        )
        // Guards against the assertion above passing because nothing changed at all.
        assertTrue("Enable-all changed nothing - the run is not exercising the use case.", changed.isNotEmpty())
        assertTrue("Expected the programs-panel switches to be part of the change.", "enableCalculator" in changed)
    }

    private fun applyEnableAll(start: AppSettings): AppSettings {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val routeAvailability = mockk<ResolvePanelRouteAvailabilityUseCase>()
        coEvery { routeAvailability.all() } returns InternalRouteCatalog.all().associate { route ->
            route.key to ResolvePanelRouteAvailabilityUseCase.Availability(
                availableInBuild = true,
                enabledAtRuntime = false,
            )
        }
        val transform = slot<suspend (AppSettings) -> AppSettings>()
        coEvery { settingsRepository.updateSettings(capture(transform)) } returns Unit
        runTest {
            ApplyEnableAllSettingsUseCase(
                settingsRepository,
                testMediaCapabilities(),
                routeAvailability,
            )()
        }
        return runBlocking { transform.captured(start) }
    }

    /**
     * Java reflection, matching the settings dump in `FastMediaSorterApp` - the kotlin-reflect runtime is
     * deliberately absent from this project's classpath.
     */
    private fun declaredBooleanSettingNames(): Set<String> =
        AppSettings::class.java.declaredFields
            .filter { !it.isSynthetic }
            .filter { !Modifier.isStatic(it.modifiers) }
            .filter { it.type == java.lang.Boolean.TYPE }
            .mapTo(mutableSetOf()) { it.name }

    private fun booleanValue(settings: AppSettings, name: String): Boolean {
        val field = AppSettings::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(settings)
    }

    private companion object {

        val CLASSIFICATION: Map<Coverage, Set<String>> = mapOf(
            Coverage.ENABLED_BY_BUTTON to setOf(
                "acceptSharedFiles",
                "allFiles",
                "disableCameraCapture",
                "disableVideoCapture",
                "embeddedGameEnabled",
                "enableCalculator",
                "enableFavorites",
                "enableNetworkMonitor",
                "enablePersistentAudioPlayback",
                "enableScheduledOperations",
                "enableSystemInfo",
                "enableWearCompanion",
                "frontFlashlightEnabled",
                "isPrimaryMediaPlayer",
                "linkAutoDownloadEnabled",
                "micRecordingEnabled",
                "screenRecordingEnabled",
                "supportAudio",
                "supportEpub",
                "supportGifs",
                "supportImages",
                "supportOfficeDocuments",
                "supportPdf",
                "supportText",
                "supportVideos",
            ),
            Coverage.ENABLED_AFTER_INSTALL to setOf(
                "enableOcr",
                "enableStreams",
                "enableTranslation",
            ),
            Coverage.PRIVACY_OR_SECURITY to setOf(
                "cameraGeotagEnabled",
                "enableStatistics",
                "recordGnssTrack",
                "secureSensitiveScreens",
            ),
            Coverage.DESTRUCTIVE_OR_DATA_RISK to setOf(
                "overwriteOnCopy",
                "overwriteOnMove",
                "useTrash",
            ),
            Coverage.COST_OPT_IN to setOf(
                "enableBackgroundSync",
                "enableThumbnailPreload",
                "searchAudioCoversOnline",
                "searchAudioCoversOnlyOnWifi",
                "thumbnailPreloadWifiOnly",
            ),
            Coverage.NEEDS_USER_RESOURCE to setOf(
                "enablePhotosDuringAudio",
                "enableSlideshowBackgroundMusic",
            ),
            Coverage.OWN_CONSENT_SURFACE to setOf(
                "gestureOverlayEnabled",
                "screenCaptureDisclosureAccepted",
                "screenRecordingDisclosureAccepted",
            ),
            Coverage.PREFERENCE_MODE_OR_STATE to setOf(
                "allowDelete",
                "allowRename",
                "allowSeparateWindow",
                "alwaysShowTouchZonesOverlay",
                "cameraCaptureCopyToClipboard",
                "cameraCaptureOpenForEditing",
                "cameraGridEnabled",
                "cameraOcrOnly",
                "cameraOcrTranslationEnabled",
                "confirmDelete",
                "confirmMove",
                "copyPanelCollapsed",
                "copyScreenshotToClipboard",
                "cropImagesToFullscreen",
                "defaultGridMode",
                "defaultRememberFileList",
                "defaultShowCommandPanel",
                "disable3dVr",
                "disableAnimations",
                "dropboxEnabled",
                "dynamicBackgroundExtension",
                "enableCopying",
                "enableMoving",
                "enablePictureInPicture",
                "enablePlayerWarmup",
                "enableSafeMode",
                "enableUndo",
                "fileOpsInOverflowMenu",
                "fileOpsOverflowMenuHintShown",
                "ftpEnabled",
                "googleDriveEnabled",
                "goToNextAfterCopy",
                "hideGridActionButtons",
                "hideSystemUiInFullscreen",
                "isCacheSizeUserModified",
                "isResourceGridMode",
                "keepScreenOnPlayer",
                "linkAutoDownloadOpenInPlayer",
                "linkDownloadAudioOnly",
                "linkDownloadLoginWallHeuristicEnabled",
                "loadFullSizeImages",
                "markdownRendered",
                "micRecordingAskFilename",
                "movePanelCollapsed",
                "nineZoneGridEnabled",
                "oneDriveEnabled",
                "openVideoInFullscreen",
                "panelStereoSingleEye",
                "pdfScrollMode",
                "playToEndInSlideshow",
                "playerFollowSystemRotation",
                "playerRotationSensorEnabled",
                "playerShowFps",
                "preventSleep",
                "programFollowSystemRotation",
                "programsPanelCollapsed",
                "rendererMigrationEnabled",
                "resourceOpsInOverflowMenu",
                "resourceTypeTabCollapsed",
                "resumeOnNextLaunch",
                "saveAudioMetadataLocally",
                "scheduledOperationsPaused",
                "sftpEnabled",
                "showBlackScreenButton",
                "showDetailedErrors",
                "showHiddenFiles",
                "showNowPlayingPanel",
                "showPdfThumbnails",
                "showPlayerHintOnFirstRun",
                "showProgramsPanelInMainWindow",
                "showSmallControls",
                "showStreamsPanelInMainWindow",
                "showSubfoldersAsItems",
                "showTextLineNumbers",
                "showVideoThumbnails",
                "skipCameraFilenameDialog",
                "smbEnabled",
                "stereoAmbiguityBestGuess",
                "stereoAutoDetectEnabled",
                "stereoTrustAspectRatio",
                "stereoTrustFilename",
                "stereoTrustMetadata",
                "streamsPanelCollapsed",
                "streamsSmartBuffering",
                "syntaxHighlighting",
                "translationLensStyle",
                "useCompactElements",
                "videoCaptureOpenInPlayer",
                "videoFrameCopyToClipboard",
                "vrAutoImmersive",
                "vrPlayerEntryPromptDismissed",
                "vrShowFps",
            ),
        )

        val classified: Set<String> = CLASSIFICATION.values.flatMapTo(mutableSetOf()) { it }
    }
}
