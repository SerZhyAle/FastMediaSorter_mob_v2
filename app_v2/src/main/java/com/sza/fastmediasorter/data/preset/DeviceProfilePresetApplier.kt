package com.sza.fastmediasorter.data.preset

import android.content.Context
import com.sza.fastmediasorter.core.compat.MultiWindowCapabilityDetector
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior
import com.sza.fastmediasorter.domain.model.PrefetchCacheMultiplier
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.model.StreamingCacheCleanupMode
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coerces a single raw CSV preset cell into the correctly typed [AppSettings] field and returns a
 * copy of the settings with that one field overridden.
 *
 * Coercion is by the field's Kotlin type. Unknown field names and unparseable values are skipped
 * with a warning, leaving the current value intact. Size fields are converted from the unit shown
 * on the corresponding Settings screen into the stored byte value (see [applySizeField]).
 */
@Singleton
class DeviceProfilePresetApplier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /**
     * Returns a copy of [settings] with [field] set from [raw], coerced by the field's type.
     * On an unknown field or an unparseable value, returns [settings] unchanged and logs a warning.
     */
    fun applyOverride(settings: AppSettings, field: String, raw: String): AppSettings {
        return when (field) {
            // ── Booleans ──────────────────────────────────────────────────
            "isResourceGridMode" -> settings.copy(isResourceGridMode = raw.toBool())
            "resourceOpsInOverflowMenu" -> settings.copy(resourceOpsInOverflowMenu = raw.toBool())
            "preventSleep" -> settings.copy(preventSleep = raw.toBool())
            "showSmallControls" -> settings.copy(showSmallControls = raw.toBool())
            "enableCalculator" -> settings.copy(enableCalculator = raw.toBool())
            "embeddedGameEnabled" -> settings.copy(embeddedGameEnabled = raw.toBool())
            "isCacheSizeUserModified" -> settings.copy(isCacheSizeUserModified = raw.toBool())
            "enableBackgroundSync" -> settings.copy(enableBackgroundSync = raw.toBool())
            "allFiles" -> settings.copy(allFiles = raw.toBool())
            "showHiddenFiles" -> settings.copy(showHiddenFiles = raw.toBool())
            "showSubfoldersAsItems" -> settings.copy(showSubfoldersAsItems = raw.toBool())
            "supportImages" -> settings.copy(supportImages = raw.toBool())
            "loadFullSizeImages" -> settings.copy(loadFullSizeImages = raw.toBool())
            "cropImagesToFullscreen" -> settings.copy(cropImagesToFullscreen = raw.toBool())
            "supportGifs" -> settings.copy(supportGifs = raw.toBool())
            "supportVideos" -> settings.copy(supportVideos = raw.toBool())
            "supportAudio" -> settings.copy(supportAudio = raw.toBool())
            "searchAudioCoversOnline" -> settings.copy(searchAudioCoversOnline = raw.toBool())
            "searchAudioCoversOnlyOnWifi" -> settings.copy(searchAudioCoversOnlyOnWifi = raw.toBool())
            "saveAudioMetadataLocally" -> settings.copy(saveAudioMetadataLocally = raw.toBool())
            "enablePhotosDuringAudio" -> settings.copy(enablePhotosDuringAudio = raw.toBool())
            "enablePersistentAudioPlayback" -> settings.copy(enablePersistentAudioPlayback = raw.toBool())
            "showNowPlayingPanel" -> settings.copy(showNowPlayingPanel = raw.toBool())
            "supportText" -> settings.copy(supportText = raw.toBool())
            "supportPdf" -> settings.copy(supportPdf = raw.toBool())
            "supportEpub" -> settings.copy(supportEpub = raw.toBool())
            "supportOfficeDocuments" -> settings.copy(supportOfficeDocuments = raw.toBool())
            "showPdfThumbnails" -> settings.copy(showPdfThumbnails = raw.toBool())
            "showTextLineNumbers" -> settings.copy(showTextLineNumbers = raw.toBool())
            "markdownRendered" -> settings.copy(markdownRendered = raw.toBool())
            "syntaxHighlighting" -> settings.copy(syntaxHighlighting = raw.toBool())
            "pdfScrollMode" -> settings.copy(pdfScrollMode = raw.toBool())
            "enableTranslation" -> settings.copy(enableTranslation = raw.toBool())
            "translationLensStyle" -> settings.copy(translationLensStyle = raw.toBool())
            "enableGoogleLens" -> settings.copy(enableGoogleLens = raw.toBool())
            "enableOcr" -> settings.copy(enableOcr = raw.toBool())
            "cameraOcrTranslationEnabled" -> settings.copy(cameraOcrTranslationEnabled = raw.toBool())
            "cameraOcrOnly" -> settings.copy(cameraOcrOnly = raw.toBool())
            "enableSlideshowBackgroundMusic" -> settings.copy(enableSlideshowBackgroundMusic = raw.toBool())
            "playToEndInSlideshow" -> settings.copy(playToEndInSlideshow = raw.toBool())
            "allowRename" -> settings.copy(allowRename = raw.toBool())
            "allowDelete" -> settings.copy(allowDelete = raw.toBool())
            "useTrash" -> settings.copy(useTrash = raw.toBool())
            "confirmDelete" -> settings.copy(confirmDelete = raw.toBool())
            "confirmMove" -> settings.copy(confirmMove = raw.toBool())
            "defaultGridMode" -> settings.copy(defaultGridMode = raw.toBool())
            "hideGridActionButtons" -> settings.copy(hideGridActionButtons = raw.toBool())
            "fileOpsInOverflowMenu" -> settings.copy(fileOpsInOverflowMenu = raw.toBool())
            "fileOpsOverflowMenuHintShown" -> settings.copy(fileOpsOverflowMenuHintShown = raw.toBool())
            "hideSystemUiInFullscreen" -> settings.copy(hideSystemUiInFullscreen = raw.toBool())
            "defaultShowCommandPanel" -> settings.copy(defaultShowCommandPanel = raw.toBool())
            "showDetailedErrors" -> settings.copy(showDetailedErrors = raw.toBool())
            "showPlayerHintOnFirstRun" -> settings.copy(showPlayerHintOnFirstRun = raw.toBool())
            "alwaysShowTouchZonesOverlay" -> settings.copy(alwaysShowTouchZonesOverlay = raw.toBool())
            "showVideoThumbnails" -> settings.copy(showVideoThumbnails = raw.toBool())
            "enablePlayerWarmup" -> settings.copy(enablePlayerWarmup = raw.toBool())
            "rendererMigrationEnabled" -> settings.copy(rendererMigrationEnabled = raw.toBool())
            "enableSafeMode" -> settings.copy(enableSafeMode = raw.toBool())
            "enableScheduledOperations" -> settings.copy(enableScheduledOperations = raw.toBool())
            "enableCopying" -> settings.copy(enableCopying = raw.toBool())
            "goToNextAfterCopy" -> settings.copy(goToNextAfterCopy = raw.toBool())
            "overwriteOnCopy" -> settings.copy(overwriteOnCopy = raw.toBool())
            "enableMoving" -> settings.copy(enableMoving = raw.toBool())
            "overwriteOnMove" -> settings.copy(overwriteOnMove = raw.toBool())
            "enableUndo" -> settings.copy(enableUndo = raw.toBool())
            "enableFavorites" -> settings.copy(enableFavorites = raw.toBool())
            "disableCameraCapture" -> settings.copy(disableCameraCapture = raw.toBool())
            "skipCameraFilenameDialog" -> settings.copy(skipCameraFilenameDialog = raw.toBool())
            "micRecordingEnabled" -> settings.copy(micRecordingEnabled = raw.toBool())
            "micRecordingAskFilename" -> settings.copy(micRecordingAskFilename = raw.toBool())
            "copyPanelCollapsed" -> settings.copy(copyPanelCollapsed = raw.toBool())
            "movePanelCollapsed" -> settings.copy(movePanelCollapsed = raw.toBool())
            "enablePictureInPicture" -> settings.copy(enablePictureInPicture = raw.toBool())
            "defaultRememberFileList" -> settings.copy(defaultRememberFileList = raw.toBool())
            "dynamicBackgroundExtension" -> settings.copy(dynamicBackgroundExtension = raw.toBool())
            "isPrimaryMediaPlayer" -> settings.copy(isPrimaryMediaPlayer = raw.toBool())
            "acceptSharedFiles" -> settings.copy(acceptSharedFiles = raw.toBool())
            "enableThumbnailPreload" -> settings.copy(enableThumbnailPreload = raw.toBool())
            "thumbnailPreloadWifiOnly" -> settings.copy(thumbnailPreloadWifiOnly = raw.toBool())
            "useCompactElements" -> settings.copy(useCompactElements = raw.toBool())
            "linkAutoDownloadEnabled" -> settings.copy(linkAutoDownloadEnabled = raw.toBool())
            "linkAutoDownloadOpenInPlayer" -> settings.copy(linkAutoDownloadOpenInPlayer = raw.toBool())
            "linkDownloadAudioOnly" -> settings.copy(linkDownloadAudioOnly = raw.toBool())
            "linkDownloadLoginWallHeuristicEnabled" -> settings.copy(linkDownloadLoginWallHeuristicEnabled = raw.toBool())
            "vrAutoImmersive" -> settings.copy(vrAutoImmersive = raw.toBool())
            "vrPlayerEntryPromptDismissed" -> settings.copy(vrPlayerEntryPromptDismissed = raw.toBool())
            "disable3dVr" -> settings.copy(disable3dVr = raw.toBool())
            "panelStereoSingleEye" -> settings.copy(panelStereoSingleEye = raw.toBool())
            "vrShowFps" -> settings.copy(vrShowFps = raw.toBool())
            "playerShowFps" -> settings.copy(playerShowFps = raw.toBool())
            "stereoAutoDetectEnabled" -> settings.copy(stereoAutoDetectEnabled = raw.toBool())
            "stereoTrustFilename" -> settings.copy(stereoTrustFilename = raw.toBool())
            "stereoTrustMetadata" -> settings.copy(stereoTrustMetadata = raw.toBool())
            "stereoTrustAspectRatio" -> settings.copy(stereoTrustAspectRatio = raw.toBool())
            "stereoAmbiguityBestGuess" -> settings.copy(stereoAmbiguityBestGuess = raw.toBool())
            "resumeOnNextLaunch" -> settings.copy(resumeOnNextLaunch = raw.toBool())
            "showBlackScreenButton" -> settings.copy(showBlackScreenButton = raw.toBool())
            "followSystemRotation" -> settings.copy(followSystemRotation = raw.toBool())
            "playerRotationSensorEnabled" -> settings.copy(playerRotationSensorEnabled = raw.toBool())

            // ── Ints ──────────────────────────────────────────────────────
            "networkParallelism" -> raw.toIntOrSkip(field) { settings.copy(networkParallelism = it) } ?: settings
            "cacheSizeMb" -> raw.toIntOrSkip(field) { settings.copy(cacheSizeMb = it) } ?: settings
            "backgroundSyncIntervalHours" -> raw.toIntOrSkip(field) { settings.copy(backgroundSyncIntervalHours = it) } ?: settings
            "slideshowInterval" -> raw.toIntOrSkip(field) { settings.copy(slideshowInterval = it) } ?: settings
            "defaultIconSize" -> raw.toIntOrSkip(field) { settings.copy(defaultIconSize = it) } ?: settings
            "maxRecipients" -> raw.toIntOrSkip(field) { settings.copy(maxRecipients = it) } ?: settings
            "streamingCacheTtlDays" -> raw.toIntOrSkip(field) { settings.copy(streamingCacheTtlDays = it) } ?: settings
            "epubHorizontalMargin" -> raw.toIntOrSkip(field) { settings.copy(epubHorizontalMargin = it) } ?: settings

            // ── Float ─────────────────────────────────────────────────────
            "epubLineHeight" ->
                raw.trim().toFloatOrNull()?.let { settings.copy(epubLineHeight = it) } ?: skip(field, raw, settings)

            // ── Long SIZE fields (CSV value in the unit shown on the Settings screen) ──
            // Images/Video screens display KB; Audio screen displays MB. textSizeMax has no editable
            // size widget (plain bytes default); treat its raw as bytes — it is empty in the CSV today.
            "imageSizeMin" -> applySizeField(field, raw, KB_TO_BYTES, settings) { s, v -> s.copy(imageSizeMin = v) }
            "imageSizeMax" -> applySizeField(field, raw, KB_TO_BYTES, settings) { s, v -> s.copy(imageSizeMax = v) }
            "videoSizeMin" -> applySizeField(field, raw, KB_TO_BYTES, settings) { s, v -> s.copy(videoSizeMin = v) }
            "videoSizeMax" -> applySizeField(field, raw, KB_TO_BYTES, settings) { s, v -> s.copy(videoSizeMax = v) }
            "audioSizeMin" -> applySizeField(field, raw, MB_TO_BYTES, settings) { s, v -> s.copy(audioSizeMin = v) }
            "audioSizeMax" -> applySizeField(field, raw, MB_TO_BYTES, settings) { s, v -> s.copy(audioSizeMax = v) }
            "textSizeMax" -> applySizeField(field, raw, BYTES_IDENTITY, settings) { s, v -> s.copy(textSizeMax = v) }

            // ── Enums (safe parse; keep current value on failure) ─────────
            "defaultSortMode" ->
                runCatching { SortMode.valueOf(raw.trim()) }.getOrNull()
                    ?.let { settings.copy(defaultSortMode = it) } ?: skip(field, raw, settings)
            "stereoDefaultLayout" -> {
                val mode = StereoMode.fromKey(raw.trim())
                // fromKey() falls back to AUTO on an unknown value; treat AUTO-from-unknown as a skip
                // unless the cell literally said AUTO.
                if (mode == StereoMode.AUTO && !raw.trim().equals("AUTO", ignoreCase = false)) {
                    skip(field, raw, settings)
                } else {
                    settings.copy(stereoDefaultLayout = mode)
                }
            }
            "stereoDefaultProjection" -> {
                val mode = StereoMode.fromKey(raw.trim())
                if (mode == StereoMode.AUTO && !raw.trim().equals("AUTO", ignoreCase = false)) {
                    skip(field, raw, settings)
                } else {
                    settings.copy(stereoDefaultProjection = mode)
                }
            }
            "prefetchCacheMultiplier" ->
                runCatching { PrefetchCacheMultiplier.valueOf(raw.trim()) }.getOrNull()
                    ?.let { settings.copy(prefetchCacheMultiplier = it) } ?: skip(field, raw, settings)
            "streamingCacheCleanupMode" ->
                runCatching { StreamingCacheCleanupMode.valueOf(raw.trim()) }.getOrNull()
                    ?.let { settings.copy(streamingCacheCleanupMode = it) } ?: skip(field, raw, settings)
            "backgroundAudioExitBehavior" ->
                runCatching { BackgroundAudioExitBehavior.valueOf(raw.trim()) }.getOrNull()
                    ?.let { settings.copy(backgroundAudioExitBehavior = it) } ?: skip(field, raw, settings)

            // ── String fields stored verbatim ─────────────────────────────
            // colorTheme accepts AUTO/LIGHT/DARK; the CSV authored BLACK as the dark variant.
            "colorTheme" -> settings.copy(colorTheme = if (raw.trim().equals("BLACK", ignoreCase = true)) "DARK" else raw.trim())
            "textReaderTheme" -> settings.copy(textReaderTheme = raw.trim())
            "pdfColorMode" -> settings.copy(pdfColorMode = raw.trim())
            "audioEmptyStateMode" -> settings.copy(audioEmptyStateMode = raw.trim())
            "ocrDefaultFontSize" -> settings.copy(ocrDefaultFontSize = raw.trim())
            "ocrDefaultFontFamily" -> settings.copy(ocrDefaultFontFamily = raw.trim())
            "ocrEngineType" -> settings.copy(ocrEngineType = raw.trim())
            "paddleOcrModel" -> settings.copy(paddleOcrModel = raw.trim())
            "vrRenderingMode" -> settings.copy(vrRenderingMode = raw.trim())
            "linkDownloadMaxResolution" -> settings.copy(linkDownloadMaxResolution = raw.trim())
            "language" -> settings.copy(language = raw.trim())
            "translationSourceLanguage" -> settings.copy(translationSourceLanguage = raw.trim())
            "translationTargetLanguage" -> settings.copy(translationTargetLanguage = raw.trim())

            // ── Special: multi-window capability gate ─────────────────────
            "allowSeparateWindow" -> {
                val value = when {
                    raw.trim().equals(CAPABILITY_TOKEN, ignoreCase = true) ->
                        MultiWindowCapabilityDetector.defaultAllowSeparateWindow(context)
                    else -> raw.toBool()
                }
                settings.copy(allowSeparateWindow = value)
            }

            // ── Unknown field name ────────────────────────────────────────
            else -> skip(field, raw, settings)
        }
    }

    private fun applySizeField(
        field: String,
        raw: String,
        multiplier: Long,
        settings: AppSettings,
        set: (AppSettings, Long) -> AppSettings
    ): AppSettings {
        val units = raw.trim().toLongOrNull() ?: return skip(field, raw, settings)
        return set(settings, units * multiplier)
    }

    private fun String.toBool(): Boolean = this.trim().equals("TRUE", ignoreCase = true)

    private inline fun String.toIntOrSkip(field: String, set: (Int) -> AppSettings): AppSettings? {
        val parsed = this.trim().toIntOrNull()
        if (parsed == null) {
            Timber.w("Device profile preset: skipped $field=$this")
            return null
        }
        return set(parsed)
    }

    private fun skip(field: String, raw: String, settings: AppSettings): AppSettings {
        Timber.w("Device profile preset: skipped $field=$raw")
        return settings
    }

    companion object {
        private const val KB_TO_BYTES = 1024L
        private const val MB_TO_BYTES = 1024L * 1024L
        private const val BYTES_IDENTITY = 1L
        private const val CAPABILITY_TOKEN = "true_if_capable"
    }
}
