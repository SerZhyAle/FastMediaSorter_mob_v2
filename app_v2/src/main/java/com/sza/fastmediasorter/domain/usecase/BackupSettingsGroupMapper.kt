package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.LauncherAllAppsSwipeAction
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeAction

/**
 * S2648: reads and writes the eight settings groups [BackupSettings] carries beside its flat fields.
 *
 * It lives beside [BackupMapper] rather than inside it because that object already declares 25 functions
 * against a ceiling of 40, and the sixteen mapping functions below would carry it past.
 *
 * Every `apply*` returns its receiver untouched when the group is absent. That early return is the whole
 * migration story: a backup written before these groups existed has none of them, so restoring one leaves
 * all 124 settings exactly as the device already had them, and `BackupPayload.CURRENT_VERSION` needs no
 * bump to say so.
 */
internal object BackupSettingsGroupMapper {

    fun toGesture(settings: AppSettings): BackupSettings.ScreenshotGesture {
        val gesture = settings.screenshotGesture
        return BackupSettings.ScreenshotGesture(
            zoneLeftTopEnabled = gesture.zoneLeftTopEnabled,
            zoneLeftBottomEnabled = gesture.zoneLeftBottomEnabled,
            zoneRightTopEnabled = gesture.zoneRightTopEnabled,
            zoneRightBottomEnabled = gesture.zoneRightBottomEnabled,
            zoneLeftTopStripVisible = gesture.zoneLeftTopStripVisible,
            zoneLeftBottomStripVisible = gesture.zoneLeftBottomStripVisible,
            zoneRightTopStripVisible = gesture.zoneRightTopStripVisible,
            zoneRightBottomStripVisible = gesture.zoneRightBottomStripVisible,
            leftTopDown = gesture.leftTopDown.name,
            leftTopRight = gesture.leftTopRight.name,
            leftTopUp = gesture.leftTopUp.name,
            leftBottomDown = gesture.leftBottomDown.name,
            leftBottomRight = gesture.leftBottomRight.name,
            leftBottomUp = gesture.leftBottomUp.name,
            rightTopDown = gesture.rightTopDown.name,
            rightTopRight = gesture.rightTopRight.name,
            rightTopUp = gesture.rightTopUp.name,
            rightBottomDown = gesture.rightBottomDown.name,
            rightBottomRight = gesture.rightBottomRight.name,
            rightBottomUp = gesture.rightBottomUp.name,
            payloadLeftTopDown = gesture.payloadLeftTopDown,
            payloadLeftTopRight = gesture.payloadLeftTopRight,
            payloadLeftTopUp = gesture.payloadLeftTopUp,
            payloadLeftBottomDown = gesture.payloadLeftBottomDown,
            payloadLeftBottomRight = gesture.payloadLeftBottomRight,
            payloadLeftBottomUp = gesture.payloadLeftBottomUp,
            payloadRightTopDown = gesture.payloadRightTopDown,
            payloadRightTopRight = gesture.payloadRightTopRight,
            payloadRightTopUp = gesture.payloadRightTopUp,
            payloadRightBottomDown = gesture.payloadRightBottomDown,
            payloadRightBottomRight = gesture.payloadRightBottomRight,
            payloadRightBottomUp = gesture.payloadRightBottomUp
        )
    }

    fun toLauncherExtra(settings: AppSettings): BackupSettings.LauncherExtra {
        val launcher = settings.launcher
        return BackupSettings.LauncherExtra(
            desktopSwipeUpAction = launcher.desktopSwipeUpAction.persistedName,
            desktopSwipeDownAction = launcher.desktopSwipeDownAction.persistedName,
            desktopSwipeLeftAction = launcher.desktopSwipeLeftAction.persistedName,
            desktopSwipeRightAction = launcher.desktopSwipeRightAction.persistedName,
            desktopSwipeUpPayload = launcher.desktopSwipeUpPayload,
            desktopSwipeDownPayload = launcher.desktopSwipeDownPayload,
            desktopSwipeLeftPayload = launcher.desktopSwipeLeftPayload,
            desktopSwipeRightPayload = launcher.desktopSwipeRightPayload,
            allAppsSwipeUpAction = launcher.allAppsSwipeUpAction.persistedName,
            allAppsSwipeDownAction = launcher.allAppsSwipeDownAction.persistedName,
            allAppsSwipeLeftAction = launcher.allAppsSwipeLeftAction.persistedName,
            allAppsSwipeRightAction = launcher.allAppsSwipeRightAction.persistedName,
            allAppsSwipeUpPayload = launcher.allAppsSwipeUpPayload,
            allAppsSwipeDownPayload = launcher.allAppsSwipeDownPayload,
            allAppsSwipeLeftPayload = launcher.allAppsSwipeLeftPayload,
            allAppsSwipeRightPayload = launcher.allAppsSwipeRightPayload,
            screenCount = launcher.screenCount,
            weatherLastLocation = launcher.weatherLastLocation,
            widgetBackdropAlpha = launcher.widgetBackdropAlpha,
            wallpaperIntensity = launcher.wallpaperIntensity,
            wallpaperAnimationSpeed = launcher.wallpaperAnimationSpeed,
            wallpaperParticleDensity = launcher.wallpaperParticleDensity,
            showScreenNumber = launcher.showScreenNumber
        )
    }

    fun toCapture(settings: AppSettings): BackupSettings.Capture = BackupSettings.Capture(
        cameraAspectRatio = settings.cameraAspectRatio,
        cameraGeotagEnabled = settings.cameraGeotagEnabled,
        cameraGridEnabled = settings.cameraGridEnabled,
        cameraOcrOnly = settings.cameraOcrOnly,
        cameraOcrTranslationEnabled = settings.cameraOcrTranslationEnabled,
        cameraPhotosDestinationResourceId = settings.cameraPhotosDestinationResourceId,
        disableVideoCapture = settings.disableVideoCapture,
        videoCaptureOpenInPlayer = settings.videoCaptureOpenInPlayer,
        videoRecordingDestinationResourceId = settings.videoRecordingDestinationResourceId,
        micRecordingEnabled = settings.micRecordingEnabled,
        micRecordingAskFilename = settings.micRecordingAskFilename,
        micRecordingDestinationResourceId = settings.micRecordingDestinationResourceId,
        screenRecordingEnabled = settings.screenRecordingEnabled,
        screenRecordingDestinationResourceId = settings.screenRecordingDestinationResourceId,
        screenshotDestinationResourceId = settings.screenshotDestinationResourceId,
        copyScreenshotToClipboard = settings.copyScreenshotToClipboard,
        gestureOverlayEnabled = settings.gestureOverlayEnabled
    )

    fun toPrograms(settings: AppSettings): BackupSettings.Programs = BackupSettings.Programs(
        enableCalculator = settings.enableCalculator,
        enableStopwatch = settings.enableStopwatch,
        stopwatchParticipantCount = settings.stopwatchParticipantCount,
        stopwatchMusicEnabled = settings.stopwatchMusicEnabled,
        stopwatchMusicUri = settings.stopwatchMusicUri,
        stopwatchVolumeKeysControl = settings.stopwatchVolumeKeysControl,
        enableNetworkMonitor = settings.enableNetworkMonitor,
        enableTourist = settings.enableTourist,
        recordGnssTrack = settings.recordGnssTrack,
        enableSystemInfo = settings.enableSystemInfo,
        enableWearCompanion = settings.enableWearCompanion,
        mirrorEnabled = settings.mirrorEnabled,
        mirrorZoomRatio = settings.mirrorZoomRatio,
        mirrorHorizontallyFlipped = settings.mirrorHorizontallyFlipped,
        mirrorBacklightOn = settings.mirrorBacklightOn,
        showProgramsPanelInMainWindow = settings.showProgramsPanelInMainWindow,
        programsPanelCollapsed = settings.programsPanelCollapsed,
        showBlackScreenButton = settings.showBlackScreenButton,
        flashlightShortcutNotificationEnabled = settings.flashlightShortcutNotificationEnabled
    )

    fun toStreams(settings: AppSettings): BackupSettings.Streams = BackupSettings.Streams(
        enableStreams = settings.enableStreams,
        streamsDefaultSort = settings.streamsDefaultSort.name,
        streamsDefaultMediaFilter = settings.streamsDefaultMediaFilter.name,
        streamsCatalogRefreshPolicy = settings.streamsCatalogRefreshPolicy.name,
        streamsDefaultAudioLanguage = settings.streamsDefaultAudioLanguage.name,
        streamsDefaultSubtitleLanguage = settings.streamsDefaultSubtitleLanguage.name,
        streamsSmartBuffering = settings.streamsSmartBuffering,
        streamsPanelCollapsed = settings.streamsPanelCollapsed,
        showStreamsPanelInMainWindow = settings.showStreamsPanelInMainWindow,
        streamingCacheCleanupMode = settings.streamingCacheCleanupMode.name,
        streamingCacheTtlDays = settings.streamingCacheTtlDays,
        prefetchCacheMultiplier = settings.prefetchCacheMultiplier.name,
        streamsVisualizeAsMusic = settings.streamsVisualizeAsMusic,
        broadcastStreamTitle = settings.broadcastStreamTitle,
        broadcastBitRateBps = settings.broadcastBitRateBps,
        broadcastPort = settings.broadcastPort,
        broadcastSampleRateHz = settings.broadcastSampleRateHz,
        broadcastChannelCount = settings.broadcastChannelCount,
        broadcastAutoOpenShare = settings.broadcastAutoOpenShare,
        enableBroadcasting = settings.enableBroadcasting,
        broadcastCameraEnabled = settings.broadcastCameraEnabled,
        broadcastMicrophoneEnabled = settings.broadcastMicrophoneEnabled,
        broadcastMicGainPercent = settings.broadcastMicGainPercent,
        broadcastVideoWidth = settings.broadcastVideoWidth,
        broadcastVideoHeight = settings.broadcastVideoHeight,
        broadcastVideoFps = settings.broadcastVideoFps,
        broadcastVideoBitrateBps = settings.broadcastVideoBitrateBps
    )

    fun toAppearance(settings: AppSettings): BackupSettings.Appearance = BackupSettings.Appearance(
        colorTheme = settings.colorTheme,
        disableAnimations = settings.disableAnimations,
        useCompactElements = settings.useCompactElements,
        resourceOpsInOverflowMenu = settings.resourceOpsInOverflowMenu,
        browseSwipeLeftAction = settings.browseSwipeLeftAction.name,
        browseSwipeRightAction = settings.browseSwipeRightAction.name,
        showNowPlayingPanel = settings.showNowPlayingPanel,
        secureSensitiveScreens = settings.secureSensitiveScreens,
        powerSavingTrigger = settings.powerSavingTrigger.name,
        unitSystem = settings.unitSystem.name,
        allowSeparateWindow = settings.allowSeparateWindow,
        enableStatistics = settings.enableStatistics,
        backgroundAudioExitBehavior = settings.backgroundAudioExitBehavior.name
    )

    fun toPlayerExtra(settings: AppSettings): BackupSettings.PlayerExtra = BackupSettings.PlayerExtra(
        playerFollowSystemRotation = settings.playerFollowSystemRotation,
        programFollowSystemRotation = settings.programFollowSystemRotation,
        playerRotationSensorEnabled = settings.playerRotationSensorEnabled,
        playerPanelAutoHideSeconds = settings.playerPanelAutoHideSeconds,
        playerShowFps = settings.playerShowFps,
        vrShowFps = settings.vrShowFps,
        vrPlayerEntryPromptDismissed = settings.vrPlayerEntryPromptDismissed,
        panelStereoSingleEye = settings.panelStereoSingleEye,
        resumeOnNextLaunch = settings.resumeOnNextLaunch,
        slideshowMusicResourceId = settings.slideshowMusicResourceId
    )

    fun toIntegration(settings: AppSettings): BackupSettings.Integration = BackupSettings.Integration(
        acceptSharedFiles = settings.acceptSharedFiles,
        isPrimaryMediaPlayer = settings.isPrimaryMediaPlayer,
        enabledShareTargets = settings.enabledShareTargets,
        disabledShareTargets = settings.disabledShareTargets,
        scheduledOperationsPaused = settings.scheduledOperationsPaused,
        suppressWearMediaTakeover = settings.suppressWearMediaTakeover
    )

    /** Applies every group present in [backup] onto [settings], leaving absent ones untouched. */
    fun applyGroups(settings: AppSettings, backup: BackupSettings): AppSettings = settings
        .applyGesture(backup.screenshotGesture)
        .applyLauncherExtra(backup.launcherExtra)
        .applyCapture(backup.capture)
        .applyPrograms(backup.programs)
        .applyStreams(backup.streams)
        .applyAppearance(backup.appearance)
        .applyPlayerExtra(backup.playerExtra)
        .applyIntegration(backup.integration)

    private fun AppSettings.applyGesture(backup: BackupSettings.ScreenshotGesture?): AppSettings {
        if (backup == null) return this
        val gesture = screenshotGesture
        return copy(
            screenshotGesture = gesture.copy(
                zoneLeftTopEnabled = backup.zoneLeftTopEnabled,
                zoneLeftBottomEnabled = backup.zoneLeftBottomEnabled,
                zoneRightTopEnabled = backup.zoneRightTopEnabled,
                zoneRightBottomEnabled = backup.zoneRightBottomEnabled,
                zoneLeftTopStripVisible = backup.zoneLeftTopStripVisible,
                zoneLeftBottomStripVisible = backup.zoneLeftBottomStripVisible,
                zoneRightTopStripVisible = backup.zoneRightTopStripVisible,
                zoneRightBottomStripVisible = backup.zoneRightBottomStripVisible,
                leftTopDown = backup.leftTopDown.toEnumOr(gesture.leftTopDown),
                leftTopRight = backup.leftTopRight.toEnumOr(gesture.leftTopRight),
                leftTopUp = backup.leftTopUp.toEnumOr(gesture.leftTopUp),
                leftBottomDown = backup.leftBottomDown.toEnumOr(gesture.leftBottomDown),
                leftBottomRight = backup.leftBottomRight.toEnumOr(gesture.leftBottomRight),
                leftBottomUp = backup.leftBottomUp.toEnumOr(gesture.leftBottomUp),
                rightTopDown = backup.rightTopDown.toEnumOr(gesture.rightTopDown),
                rightTopRight = backup.rightTopRight.toEnumOr(gesture.rightTopRight),
                rightTopUp = backup.rightTopUp.toEnumOr(gesture.rightTopUp),
                rightBottomDown = backup.rightBottomDown.toEnumOr(gesture.rightBottomDown),
                rightBottomRight = backup.rightBottomRight.toEnumOr(gesture.rightBottomRight),
                rightBottomUp = backup.rightBottomUp.toEnumOr(gesture.rightBottomUp),
                payloadLeftTopDown = backup.payloadLeftTopDown ?: gesture.payloadLeftTopDown,
                payloadLeftTopRight = backup.payloadLeftTopRight ?: gesture.payloadLeftTopRight,
                payloadLeftTopUp = backup.payloadLeftTopUp ?: gesture.payloadLeftTopUp,
                payloadLeftBottomDown = backup.payloadLeftBottomDown ?: gesture.payloadLeftBottomDown,
                payloadLeftBottomRight = backup.payloadLeftBottomRight ?: gesture.payloadLeftBottomRight,
                payloadLeftBottomUp = backup.payloadLeftBottomUp ?: gesture.payloadLeftBottomUp,
                payloadRightTopDown = backup.payloadRightTopDown ?: gesture.payloadRightTopDown,
                payloadRightTopRight = backup.payloadRightTopRight ?: gesture.payloadRightTopRight,
                payloadRightTopUp = backup.payloadRightTopUp ?: gesture.payloadRightTopUp,
                payloadRightBottomDown = backup.payloadRightBottomDown ?: gesture.payloadRightBottomDown,
                payloadRightBottomRight = backup.payloadRightBottomRight
                    ?: gesture.payloadRightBottomRight,
                payloadRightBottomUp = backup.payloadRightBottomUp ?: gesture.payloadRightBottomUp
            )
        )
    }

    private fun AppSettings.applyLauncherExtra(backup: BackupSettings.LauncherExtra?): AppSettings {
        if (backup == null) return this
        val current = launcher
        return copy(
            launcher = current.copy(
                desktopSwipeUpAction = LauncherDesktopSwipeAction
                    .fromName(backup.desktopSwipeUpAction, current.desktopSwipeUpAction),
                desktopSwipeDownAction = LauncherDesktopSwipeAction
                    .fromName(backup.desktopSwipeDownAction, current.desktopSwipeDownAction),
                desktopSwipeLeftAction = LauncherDesktopSwipeAction
                    .fromName(backup.desktopSwipeLeftAction, current.desktopSwipeLeftAction),
                desktopSwipeRightAction = LauncherDesktopSwipeAction
                    .fromName(backup.desktopSwipeRightAction, current.desktopSwipeRightAction),
                desktopSwipeUpPayload = backup.desktopSwipeUpPayload ?: current.desktopSwipeUpPayload,
                desktopSwipeDownPayload = backup.desktopSwipeDownPayload
                    ?: current.desktopSwipeDownPayload,
                desktopSwipeLeftPayload = backup.desktopSwipeLeftPayload
                    ?: current.desktopSwipeLeftPayload,
                desktopSwipeRightPayload = backup.desktopSwipeRightPayload
                    ?: current.desktopSwipeRightPayload,
                allAppsSwipeUpAction = LauncherAllAppsSwipeAction
                    .fromName(backup.allAppsSwipeUpAction, current.allAppsSwipeUpAction),
                allAppsSwipeDownAction = LauncherAllAppsSwipeAction
                    .fromName(backup.allAppsSwipeDownAction, current.allAppsSwipeDownAction),
                allAppsSwipeLeftAction = LauncherAllAppsSwipeAction
                    .fromName(backup.allAppsSwipeLeftAction, current.allAppsSwipeLeftAction),
                allAppsSwipeRightAction = LauncherAllAppsSwipeAction
                    .fromName(backup.allAppsSwipeRightAction, current.allAppsSwipeRightAction),
                allAppsSwipeUpPayload = backup.allAppsSwipeUpPayload ?: current.allAppsSwipeUpPayload,
                allAppsSwipeDownPayload = backup.allAppsSwipeDownPayload
                    ?: current.allAppsSwipeDownPayload,
                allAppsSwipeLeftPayload = backup.allAppsSwipeLeftPayload
                    ?: current.allAppsSwipeLeftPayload,
                allAppsSwipeRightPayload = backup.allAppsSwipeRightPayload
                    ?: current.allAppsSwipeRightPayload,
                screenCount = backup.screenCount,
                weatherLastLocation = backup.weatherLastLocation ?: current.weatherLastLocation,
                widgetBackdropAlpha = backup.widgetBackdropAlpha,
                wallpaperIntensity = backup.wallpaperIntensity
                    ?.let(AppSettings::coerceLauncherWallpaperIntensity)
                    ?: current.wallpaperIntensity,
                wallpaperAnimationSpeed = backup.wallpaperAnimationSpeed
                    ?.let(AppSettings::coerceLauncherWallpaperAnimationSpeed)
                    ?: current.wallpaperAnimationSpeed,
                wallpaperParticleDensity = backup.wallpaperParticleDensity
                    ?.let(AppSettings::coerceLauncherWallpaperParticleDensity)
                    ?: current.wallpaperParticleDensity,
                showScreenNumber = backup.showScreenNumber ?: current.showScreenNumber
            )
        )
    }

    private fun AppSettings.applyCapture(backup: BackupSettings.Capture?): AppSettings {
        if (backup == null) return this
        return copy(
            cameraAspectRatio = backup.cameraAspectRatio,
            cameraGeotagEnabled = backup.cameraGeotagEnabled,
            cameraGridEnabled = backup.cameraGridEnabled,
            cameraOcrOnly = backup.cameraOcrOnly,
            cameraOcrTranslationEnabled = backup.cameraOcrTranslationEnabled,
            cameraPhotosDestinationResourceId = backup.cameraPhotosDestinationResourceId,
            disableVideoCapture = backup.disableVideoCapture,
            videoCaptureOpenInPlayer = backup.videoCaptureOpenInPlayer,
            videoRecordingDestinationResourceId = backup.videoRecordingDestinationResourceId,
            micRecordingEnabled = backup.micRecordingEnabled,
            micRecordingAskFilename = backup.micRecordingAskFilename,
            micRecordingDestinationResourceId = backup.micRecordingDestinationResourceId,
            screenRecordingEnabled = backup.screenRecordingEnabled,
            screenRecordingDestinationResourceId = backup.screenRecordingDestinationResourceId,
            screenshotDestinationResourceId = backup.screenshotDestinationResourceId,
            copyScreenshotToClipboard = backup.copyScreenshotToClipboard,
            gestureOverlayEnabled = backup.gestureOverlayEnabled
        )
    }

    private fun AppSettings.applyPrograms(backup: BackupSettings.Programs?): AppSettings {
        if (backup == null) return this
        return copy(
            enableCalculator = backup.enableCalculator,
            enableStopwatch = backup.enableStopwatch,
            stopwatchParticipantCount = backup.stopwatchParticipantCount,
            stopwatchMusicEnabled = backup.stopwatchMusicEnabled,
            stopwatchMusicUri = backup.stopwatchMusicUri ?: stopwatchMusicUri,
            stopwatchVolumeKeysControl = backup.stopwatchVolumeKeysControl,
            enableNetworkMonitor = backup.enableNetworkMonitor,
            recordGnssTrack = backup.recordGnssTrack,
            enableSystemInfo = backup.enableSystemInfo,
            enableTourist = backup.enableTourist,
            enableWearCompanion = backup.enableWearCompanion,
            mirrorEnabled = backup.mirrorEnabled,
            mirrorZoomRatio = backup.mirrorZoomRatio,
            mirrorHorizontallyFlipped = backup.mirrorHorizontallyFlipped,
            mirrorBacklightOn = backup.mirrorBacklightOn,
            showProgramsPanelInMainWindow = backup.showProgramsPanelInMainWindow,
            programsPanelCollapsed = backup.programsPanelCollapsed,
            showBlackScreenButton = backup.showBlackScreenButton,
            flashlightShortcutNotificationEnabled = backup.flashlightShortcutNotificationEnabled
                ?: flashlightShortcutNotificationEnabled
        )
    }

    private fun AppSettings.applyStreams(backup: BackupSettings.Streams?): AppSettings {
        if (backup == null) return this
        return copy(
            enableStreams = backup.enableStreams,
            streamsDefaultSort = backup.streamsDefaultSort.toEnumOr(streamsDefaultSort),
            streamsDefaultMediaFilter = backup.streamsDefaultMediaFilter
                .toEnumOr(streamsDefaultMediaFilter),
            streamsCatalogRefreshPolicy = backup.streamsCatalogRefreshPolicy
                .toEnumOr(streamsCatalogRefreshPolicy),
            streamsDefaultAudioLanguage = backup.streamsDefaultAudioLanguage
                .toEnumOr(streamsDefaultAudioLanguage),
            streamsDefaultSubtitleLanguage = backup.streamsDefaultSubtitleLanguage
                .toEnumOr(streamsDefaultSubtitleLanguage),
            streamsSmartBuffering = backup.streamsSmartBuffering,
            streamsPanelCollapsed = backup.streamsPanelCollapsed,
            showStreamsPanelInMainWindow = backup.showStreamsPanelInMainWindow,
            streamingCacheCleanupMode = backup.streamingCacheCleanupMode
                .toEnumOr(streamingCacheCleanupMode),
            streamingCacheTtlDays = backup.streamingCacheTtlDays,
            prefetchCacheMultiplier = backup.prefetchCacheMultiplier.toEnumOr(prefetchCacheMultiplier),
            streamsVisualizeAsMusic = backup.streamsVisualizeAsMusic ?: streamsVisualizeAsMusic,
            broadcastStreamTitle = backup.broadcastStreamTitle ?: broadcastStreamTitle,
            broadcastBitRateBps = backup.broadcastBitRateBps ?: broadcastBitRateBps,
            broadcastPort = backup.broadcastPort ?: broadcastPort,
            broadcastSampleRateHz = backup.broadcastSampleRateHz ?: broadcastSampleRateHz,
            broadcastChannelCount = backup.broadcastChannelCount ?: broadcastChannelCount,
            broadcastAutoOpenShare = backup.broadcastAutoOpenShare ?: broadcastAutoOpenShare,
            enableBroadcasting = backup.enableBroadcasting ?: enableBroadcasting,
            broadcastCameraEnabled = backup.broadcastCameraEnabled ?: broadcastCameraEnabled,
            broadcastMicrophoneEnabled = backup.broadcastMicrophoneEnabled ?: broadcastMicrophoneEnabled,
            broadcastMicGainPercent = backup.broadcastMicGainPercent ?: broadcastMicGainPercent,
            broadcastVideoWidth = backup.broadcastVideoWidth ?: broadcastVideoWidth,
            broadcastVideoHeight = backup.broadcastVideoHeight ?: broadcastVideoHeight,
            broadcastVideoFps = backup.broadcastVideoFps ?: broadcastVideoFps,
            broadcastVideoBitrateBps = backup.broadcastVideoBitrateBps ?: broadcastVideoBitrateBps
        )
    }

    private fun AppSettings.applyAppearance(backup: BackupSettings.Appearance?): AppSettings {
        if (backup == null) return this
        return copy(
            colorTheme = backup.colorTheme ?: colorTheme,
            disableAnimations = backup.disableAnimations,
            useCompactElements = backup.useCompactElements,
            resourceOpsInOverflowMenu = backup.resourceOpsInOverflowMenu,
            browseSwipeLeftAction = backup.browseSwipeLeftAction.toEnumOr(browseSwipeLeftAction),
            browseSwipeRightAction = backup.browseSwipeRightAction.toEnumOr(browseSwipeRightAction),
            showNowPlayingPanel = backup.showNowPlayingPanel,
            secureSensitiveScreens = backup.secureSensitiveScreens,
            powerSavingTrigger = backup.powerSavingTrigger.toEnumOr(powerSavingTrigger),
            unitSystem = backup.unitSystem.toEnumOr(unitSystem),
            allowSeparateWindow = backup.allowSeparateWindow,
            enableStatistics = backup.enableStatistics,
            backgroundAudioExitBehavior = backup.backgroundAudioExitBehavior
                .toEnumOr(backgroundAudioExitBehavior)
        )
    }

    private fun AppSettings.applyPlayerExtra(backup: BackupSettings.PlayerExtra?): AppSettings {
        if (backup == null) return this
        return copy(
            playerFollowSystemRotation = backup.playerFollowSystemRotation,
            programFollowSystemRotation = backup.programFollowSystemRotation,
            playerRotationSensorEnabled = backup.playerRotationSensorEnabled,
            playerPanelAutoHideSeconds = backup.playerPanelAutoHideSeconds,
            playerShowFps = backup.playerShowFps,
            vrShowFps = backup.vrShowFps,
            vrPlayerEntryPromptDismissed = backup.vrPlayerEntryPromptDismissed,
            panelStereoSingleEye = backup.panelStereoSingleEye,
            resumeOnNextLaunch = backup.resumeOnNextLaunch,
            slideshowMusicResourceId = backup.slideshowMusicResourceId
        )
    }

    private fun AppSettings.applyIntegration(backup: BackupSettings.Integration?): AppSettings {
        if (backup == null) return this
        return copy(
            acceptSharedFiles = backup.acceptSharedFiles,
            isPrimaryMediaPlayer = backup.isPrimaryMediaPlayer,
            enabledShareTargets = backup.enabledShareTargets ?: enabledShareTargets,
            disabledShareTargets = backup.disabledShareTargets ?: disabledShareTargets,
            scheduledOperationsPaused = backup.scheduledOperationsPaused,
            suppressWearMediaTakeover = backup.suppressWearMediaTakeover ?: suppressWearMediaTakeover
        )
    }
}

/**
 * S2648: resolves an enum token, falling back on both an absent key and a name this build does not know.
 *
 * The fallback is always the value the device currently holds, so a slot configured by a newer build
 * degrades to what the user already had rather than to the class default - the same contract
 * `LauncherDesktopSwipeAction.fromName` offers for the two swipe families.
 */
private inline fun <reified T : Enum<T>> String?.toEnumOr(fallback: T): T =
    this?.let { token -> enumValues<T>().firstOrNull { it.name == token } } ?: fallback
