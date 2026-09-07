package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * Force-enables every setting that unlocks a capability this build actually carries (S0409, widened by
 * S2382).
 *
 * Membership rule: a setting joins when switching it on unlocks a capability the build already ships and
 * the user has nothing further to choose. That includes the flags deciding whether a file operation is
 * offered at all - copy, move, undo, rename (S2674). Out of scope by rule: privacy and telemetry
 * switches, destructive defaults (delete stays out by that clause), preferences and modes, numeric
 * values, settings inert until the user picks a resource, settings whose default a device detector
 * computes rather than the build, and settings that own a consent page in the wizard. The classification
 * of every boolean in the settings model is pinned by `EnableAllCoverageClassificationTest`, which fails
 * on an unclassified new field - the previous hand-written list drifted away from this KDoc unnoticed.
 *
 * The «Send to..» recipient registry splits along the same rule (S2684): the button clears the explicit
 * opt-outs, so a recipient this build ships on comes back, and never touches the explicit opt-ins, so a
 * recipient the registry ships off is not force-added. Which messenger to send files to is a choice the
 * user still has, which is the clause that keeps the `ALWAYS_OFF` half out.
 *
 * Deliverable-gated features (OCR/translation) are intentionally NOT touched here - they are downloaded
 * and enabled-on-install by the welcome orchestrator, preserving the "enable only after install" invariant.
 */
class ApplyEnableAllSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mediaCapabilities: MediaCapabilities,
    private val routeAvailability: ResolvePanelRouteAvailabilityUseCase,
) {
    /**
     * Read-modify-write of the latest snapshot so a concurrent profile/preset write is not clobbered.
     * The capability gate implements the "silently skip unavailable" decision: a feature absent from
     * this build/flavor keeps its current value instead of being force-enabled.
     *
     * S0876: uses the transform overload (mutex-serialized read+write, see SettingsRepository KDoc) -
     * the welcome enable-all flow spawns concurrent deliverable-install writers right after this call.
     */
    suspend operator fun invoke() {
        Timber.d("S2664: enable-all pressed - remote sources join the button")
        // S2382: resolved BEFORE updateSettings and never inside its transform - the transform runs under
        // the repository mutex while all() performs a settings read of its own.
        val compiledRoutes = routeAvailability.all()
            .filterValues { it.availableInBuild }
            .keys

        settingsRepository.updateSettings { current ->
            val withMediaTypes = current.copy(
                allFiles = true,
                supportImages = if (mediaCapabilities.supportsImages) true else current.supportImages,
                supportGifs = true,
                supportAudio = if (mediaCapabilities.supportsAudio) true else current.supportAudio,
                supportVideos = if (mediaCapabilities.supportsVideo) true else current.supportVideos,
                supportText = if (mediaCapabilities.supportsDocuments) true else current.supportText,
                supportPdf = if (mediaCapabilities.supportsDocuments) true else current.supportPdf,
                supportOfficeDocuments = if (mediaCapabilities.supportsDocuments) {
                    true
                } else {
                    current.supportOfficeDocuments
                },
                supportEpub = if (mediaCapabilities.supportsEpub) true else current.supportEpub,
                enablePersistentAudioPlayback = if (mediaCapabilities.supportsAudio) {
                    true
                } else {
                    current.enablePersistentAudioPlayback
                },
                acceptSharedFiles = true,
                isPrimaryMediaPlayer = true,
            )
            // S2674: the flags deciding whether a file operation is OFFERED, not how it behaves. Delete is
            // absent on purpose - it is the one whose return the user cannot undo, so it stays with the
            // destructive defaults the rule keeps out.
            Timber.d("S2674: enable-all switching the file-operation flags on")
            val withFileOperations = withMediaTypes.copy(
                enableCopying = true,
                enableMoving = true,
                enableUndo = true,
                allowRename = true,
            )
            // S2674: PiP is a player capability, so a build carrying no player keeps its stored value.
            // The device axis needs no condition here - PictureInPictureManager asks the platform for
            // FEATURE_PICTURE_IN_PICTURE and hides the button on its own.
            val withPictureInPicture = if (mediaCapabilities.supportsVideo || mediaCapabilities.supportsAudio) {
                withFileOperations.copy(enablePictureInPicture = true)
            } else {
                withFileOperations
            }
            // S2664: the six remote sources join the button. The compile-tier question goes to
            // MediaCapabilities - the same two flags RemoteSourceAvailabilityGate consults - so a source
            // this build does not carry keeps its stored value instead of being switched on.
            val withNetworkSources = if (mediaCapabilities.supportsLocalNetworkSources) {
                withPictureInPicture.copy(smbEnabled = true, sftpEnabled = true, ftpEnabled = true)
            } else {
                withPictureInPicture
            }
            val withRemoteSources = if (mediaCapabilities.supportsCloud) {
                withNetworkSources.copy(googleDriveEnabled = true, oneDriveEnabled = true, dropboxEnabled = true)
            } else {
                withNetworkSources
            }
            // S2684: only the explicit opt-outs are dropped, returning every recipient to the registry
            // default ShareTargetAvailabilityResolver computes. Availability needs no condition here -
            // IsShareTargetEnabledUseCase asks the resolver before either set, so an unreachable recipient
            // stays off whatever the settings hold.
            val withShareTargets = withRemoteSources.copy(disabledShareTargets = emptySet())
            ROUTE_ENABLERS.entries.fold(withShareTargets) { settings, (routeKey, enable) ->
                if (routeKey in compiledRoutes) enable(settings) else settings
            }
        }
        Timber.i(
            "ApplyEnableAllSettingsUseCase: enable-all applied (audio=%b video=%b docs=%b routes=%d)",
            mediaCapabilities.supportsAudio,
            mediaCapabilities.supportsVideo,
            mediaCapabilities.supportsDocuments,
            compiledRoutes.size,
        )
    }

    private companion object {
        /**
         * S2382: which settings field carries each feature route's runtime half. The capability half is
         * deliberately absent - `availableInBuild` already answers it, so restating a condition here
         * would be the hand-listing S1736 removed, reintroduced one layer up.
         *
         * One entry per FIELD, not per route: the photo-capture and camera-launch routes read the same
         * `disableCameraCapture` the quick-camera route does, so they follow from its entry.
         */
        private val ROUTE_ENABLERS: Map<String, (AppSettings) -> AppSettings> = mapOf(
            InternalRouteCatalog.KEY_CALCULATOR to { s: AppSettings -> s.copy(enableCalculator = true) },
            // S1411: the stopwatch is present wherever the calculator is (strategic criterion 1), so the
            // button that turns the calculator on turns this on too. Only the master switch joins - the
            // tool's music and volume-key settings are a mode and a resource choice, which the membership
            // rule keeps out.
            InternalRouteCatalog.KEY_STOPWATCH to { s: AppSettings ->
                Timber.d("S1411: enable-all switching the stopwatch on")
                s.copy(enableStopwatch = true)
            },
            InternalRouteCatalog.KEY_NETWORK_MONITOR to { s: AppSettings -> s.copy(enableNetworkMonitor = true) },
            InternalRouteCatalog.KEY_GAME to { s: AppSettings -> s.copy(embeddedGameEnabled = true) },
            InternalRouteCatalog.KEY_SYSTEM_INFO to { s: AppSettings -> s.copy(enableSystemInfo = true) },
            InternalRouteCatalog.KEY_WEAR_COMPANION to { s: AppSettings -> s.copy(enableWearCompanion = true) },
            InternalRouteCatalog.KEY_FAVORITES to { s: AppSettings -> s.copy(enableFavorites = true) },
            InternalRouteCatalog.KEY_QUICK_CAMERA to { s: AppSettings -> s.copy(disableCameraCapture = false) },
            InternalRouteCatalog.KEY_QUICK_VOICE to { s: AppSettings -> s.copy(micRecordingEnabled = true) },
            InternalRouteCatalog.KEY_SCREEN_RECORDING to { s: AppSettings -> s.copy(screenRecordingEnabled = true) },
            InternalRouteCatalog.KEY_LINK_DOWNLOAD to { s: AppSettings -> s.copy(linkAutoDownloadEnabled = true) },
            InternalRouteCatalog.KEY_FRONT_FLASHLIGHT to { s: AppSettings -> s.copy(frontFlashlightEnabled = true) },
            InternalRouteCatalog.KEY_WATER_FLASHLIGHT to { s: AppSettings -> s.copy(waterFlashlightEnabled = true) },
            // S2628: the mirror joins on the same footing as the other sub-programs, so a user who switched
            // it off by hand gets it back from the button. Its default is already true, which is why the
            // omission survived S1924 unseen - the second half of EnableAllCoverageClassificationTest
            // compares before against after and a field that starts on can never appear in that diff. Only
            // the master switch joins: flip and backlight are modes the membership rule keeps out.
            InternalRouteCatalog.KEY_MIRROR to { s: AppSettings -> s.copy(mirrorEnabled = true) },
            InternalRouteCatalog.KEY_START_VIDEO_RECORDING to { s: AppSettings -> s.copy(disableVideoCapture = false) },
            InternalRouteCatalog.KEY_SCHEDULED_TASKS to { s: AppSettings ->
                s.copy(enableScheduledOperations = true)
            },
        )
    }
}
