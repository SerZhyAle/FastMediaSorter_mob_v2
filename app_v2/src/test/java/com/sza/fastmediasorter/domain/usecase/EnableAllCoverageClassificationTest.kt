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

    /**
     * Starts from all six sources off, because they default to on and the diff test above can never
     * observe a field that starts true - the blindness S2628 recorded when the mirror's omission
     * survived unseen.
     */
    @Test
    fun `the button switches the remote sources back on when the user turned them off`() {
        val before = AppSettings(
            smbEnabled = false,
            sftpEnabled = false,
            ftpEnabled = false,
            googleDriveEnabled = false,
            oneDriveEnabled = false,
            dropboxEnabled = false,
        )
        val after = applyEnableAll(before)
        assertTrue("Enable-all left SMB off on a build that carries it.", after.smbEnabled)
        assertTrue("Enable-all left SFTP off on a build that carries it.", after.sftpEnabled)
        assertTrue("Enable-all left FTP off on a build that carries it.", after.ftpEnabled)
        assertTrue("Enable-all left Google Drive off on a build that carries it.", after.googleDriveEnabled)
        assertTrue("Enable-all left OneDrive off on a build that carries it.", after.oneDriveEnabled)
        assertTrue("Enable-all left Dropbox off on a build that carries it.", after.dropboxEnabled)
    }

    /**
     * S2684: the «Send to..» registry is two sets of ids rather than a boolean, so no [Coverage] bucket can
     * hold its decision and this is the only place the answer is pinned. The two halves are asserted
     * together on purpose - clearing the opt-outs while also clearing the opt-ins would read as the same
     * "recipients came back" in a test that watched one set.
     */
    @Test
    fun `the button drops the share-target opt-outs and keeps the opt-ins`() {
        val before = AppSettings(
            disabledShareTargets = setOf("print", "email"),
            enabledShareTargets = setOf("telegram"),
        )
        val after = applyEnableAll(before)
        assertEquals(
            "Enable-all left a recipient the user switched off, so a build-default target stayed hidden.",
            emptySet<String>(),
            after.disabledShareTargets,
        )
        assertEquals(
            "Enable-all rewrote the explicit opt-ins - a recipient the user chose by hand must survive it.",
            setOf("telegram"),
            after.enabledShareTargets,
        )
    }

    /**
     * S2674: all five default to on, so - exactly as with the remote sources above - the diff test can
     * never observe them and the run starts from a user who switched every one of them off.
     */
    @Test
    fun `the button switches the file-operation flags back on when the user turned them off`() {
        val before = AppSettings(
            enableCopying = false,
            enableMoving = false,
            enableUndo = false,
            enablePictureInPicture = false,
            allowRename = false,
        )
        val after = applyEnableAll(before)
        assertTrue("Enable-all left copying off.", after.enableCopying)
        assertTrue("Enable-all left moving off.", after.enableMoving)
        assertTrue("Enable-all left undo off.", after.enableUndo)
        assertTrue(
            "Enable-all left picture-in-picture off on a build that carries a player.",
            after.enablePictureInPicture,
        )
        assertTrue("Enable-all left renaming off.", after.allowRename)
    }

    /**
     * S2674: [CLASSIFICATION] can only cover booleans, so a capability expressed as an enum or a number
     * carries no coverage decision at all. The gate is deliberately narrowed to names that ANNOUNCE a
     * capability: the settings model holds about a hundred ordinary non-boolean fields - intervals,
     * languages, sort modes - and failing on each new one would train the reader to re-baseline without
     * looking. The two share-target sets it does catch are excused by name, each against the ticket that
     * owes the answer - an excuse is a carried question, never a silenced one.
     */
    @Test
    fun `no capability-shaped setting hides behind a non-boolean type`() {
        val suspicious = AppSettings::class.java.declaredFields
            .filter { !it.isSynthetic }
            .filter { !Modifier.isStatic(it.modifiers) }
            .filter { it.type != java.lang.Boolean.TYPE }
            .map { it.name }
            .filterTo(mutableSetOf()) { name ->
                CAPABILITY_NAME_PREFIXES.any { name.startsWith(it) } || name.endsWith("Enabled")
            }
        suspicious -= CAPABILITY_SHAPED_EXCUSED.keys
        suspicious -= CAPABILITY_SHAPED_DECIDED.keys
        assertEquals(
            "A setting whose name announces a capability is not a Boolean, so no coverage decision can " +
                "reach it. Either give it a boolean master switch this file classifies, rename it to " +
                "describe the mode it actually picks, or excuse it in CAPABILITY_SHAPED_EXCUSED against " +
                "the ticket that owes the answer.",
            emptySet<String>(),
            suspicious,
        )
        val declaredNames = AppSettings::class.java.declaredFields.mapTo(mutableSetOf()) { it.name }
        assertEquals(
            "An excused name is no longer a field in AppSettings - drop it from CAPABILITY_SHAPED_EXCUSED.",
            emptySet<String>(),
            CAPABILITY_SHAPED_EXCUSED.keys - declaredNames,
        )
        assertEquals(
            "A decided name is no longer a field in AppSettings - drop it from CAPABILITY_SHAPED_DECIDED.",
            emptySet<String>(),
            CAPABILITY_SHAPED_DECIDED.keys - declaredNames,
        )
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

        /** S2674: name shapes that claim a capability exists, rather than picking how one behaves. */
        val CAPABILITY_NAME_PREFIXES = listOf("enable", "allow", "disable", "support")

        /**
         * S2674: a capability-shaped non-boolean whose coverage question is real but belongs to another
         * ticket. The value is that ticket, so the excuse names its owner and expires when the owner
         * answers. Empty is the healthy state: every such field is either decided below or has no ticket
         * yet, and the map stays here so the next one is parked rather than deleted.
         */
        val CAPABILITY_SHAPED_EXCUSED: Map<String, String> = emptyMap()

        /**
         * S2684: a capability-shaped non-boolean whose coverage question has been answered, against the
         * answer itself rather than against a ticket. Separate from [CAPABILITY_SHAPED_EXCUSED] because
         * the two mean opposite things to a reader deciding whether a field still owes work; the behaviour
         * behind each entry is pinned by `the button drops the share-target opt-outs and keeps the
         * opt-ins`, since a set of ids fits no [Coverage] bucket.
         */
        val CAPABILITY_SHAPED_DECIDED: Map<String, String> = mapOf(
            "disabledShareTargets" to "Cleared by the button - a recipient the build ships on comes back.",
            "enabledShareTargets" to "Untouched - the registry's ALWAYS_OFF recipients stay the user's choice.",
        )

        val CLASSIFICATION: Map<Coverage, Set<String>> = mapOf(
            Coverage.ENABLED_BY_BUTTON to setOf(
                "acceptSharedFiles",
                "allFiles",
                "allowRename",
                "disableCameraCapture",
                "disableVideoCapture",
                "dropboxEnabled",
                "embeddedGameEnabled",
                "enableBroadcasting",
                "enableCalculator",
                "enableCopying",
                "enableFavorites",
                "enableMoving",
                "enableNetworkMonitor",
                "enablePersistentAudioPlayback",
                "enablePictureInPicture",
                "enableScheduledOperations",
                "enableStopwatch",
                "enableSystemInfo",
                "enableTourist",
                "enableUndo",
                "enableWearCompanion",
                "frontFlashlightEnabled",
                "ftpEnabled",
                "googleDriveEnabled",
                "isPrimaryMediaPlayer",
                "linkAutoDownloadEnabled",
                "micRecordingEnabled",
                "mirrorEnabled",
                "oneDriveEnabled",
                "screenRecordingEnabled",
                "sftpEnabled",
                "smbEnabled",
                "supportAudio",
                "supportEpub",
                "supportGifs",
                "supportImages",
                "supportOfficeDocuments",
                "supportPdf",
                "supportText",
                "supportVideos",
                "waterFlashlightEnabled",
            ),
            Coverage.ENABLED_AFTER_INSTALL to setOf(
                "cameraOcrTranslationEnabled",
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
                "allowDelete",
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
                "stopwatchMusicEnabled",
            ),
            Coverage.OWN_CONSENT_SURFACE to setOf(
                "gestureOverlayEnabled",
                "screenCaptureDisclosureAccepted",
                "screenRecordingDisclosureAccepted",
            ),
            Coverage.PREFERENCE_MODE_OR_STATE to setOf(
                // S2674: a device property, not a build one - MultiWindowCapabilityDetector computes its
                // first-run default, and S0184 decided that on an ordinary phone "open in a new window"
                // must not appear. A button switching it on everywhere would restore that defect.
                "allowSeparateWindow",
                "alwaysShowTouchZonesOverlay",
                "broadcastAutoOpenShare",
                "cameraCaptureCopyToClipboard",
                "cameraCaptureOpenForEditing",
                "cameraGridEnabled",
                "cameraOcrOnly",
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
                "dynamicBackgroundExtension",
                "enablePlayerWarmup",
                "enableSafeMode",
                "fileOpsInOverflowMenu",
                "fileOpsOverflowMenuHintShown",
                "flashlightShortcutNotificationEnabled",
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
                "mirrorBacklightOn",
                "mirrorHorizontallyFlipped",
                "movePanelCollapsed",
                "nineZoneGridEnabled",
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
                "stereoAmbiguityBestGuess",
                "stereoAutoDetectEnabled",
                "stereoTrustAspectRatio",
                "stereoTrustFilename",
                "stereoTrustMetadata",
                "stopwatchVolumeKeysControl",
                "streamsPanelCollapsed",
                "streamsSmartBuffering",
                "streamsVisualizeAsMusic",
                "suppressWearMediaTakeover",
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
