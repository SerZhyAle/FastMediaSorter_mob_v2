package com.sza.fastmediasorter.domain.usecase.panel

import android.content.Context
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.screencapture.ScreenVideoRecordingController
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Answers, per feature route key, whether the route is compiled into this build and whether it is
 * enabled at runtime (strategic S0663 §5.1.B). Build/runtime availability is sourced only from the
 * existing single sources of truth - [CapabilityAvailability] / [MediaCapabilities] for compile-time
 * capability flags, the multibound [ScreenVideoRecordingController] set for the screen-capture engine,
 * and [SettingsRepository] for runtime toggles - never from build flags directly (CLAUDE.md Rule 15).
 */
class ResolvePanelRouteAvailabilityUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capability: CapabilityAvailability,
    private val settingsRepository: SettingsRepository,
    private val mediaCapabilities: MediaCapabilities,
    private val screenVideoRecordingControllers: Set<@JvmSuppressWildcards ScreenVideoRecordingController>,
) {

    /**
     * [availableInBuild] - feature is compiled into this flavor. [enabledAtRuntime] - a runtime
     * toggle (where one exists) is on. A route is launchable only when both hold; a compiled but
     * disabled route routes to its setting instead of dead-launching (§6.1).
     */
    data class Availability(val availableInBuild: Boolean, val enabledAtRuntime: Boolean) {
        val isLaunchable: Boolean get() = availableInBuild && enabledAtRuntime
    }

    suspend operator fun invoke(routeKey: String): Availability = withContext(Dispatchers.IO) {
        resolve(routeKey, settingsRepository.getSettings().first())
    }

    /** Availability for every catalog route in one settings read (used by the picker and the seed). */
    suspend fun all(): Map<String, Availability> = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getSettings().first()
        InternalRouteCatalog.all().associate { route ->
            route.key to resolve(route.key, settings)
        }
    }

    // S0912: every route's availability lives in this one branch, so a future route cannot silently
    // drift into "insufficient": either it declares its own availableInBuild/enabledAtRuntime pair
    // here, or the `else` below reports it unavailable - there is no second toggle to forget.
    private fun resolve(routeKey: String, settings: AppSettings): Availability =
        when (routeKey) {
            InternalRouteCatalog.KEY_CALCULATOR -> Availability(availableInBuild = true, enabledAtRuntime = true)
            InternalRouteCatalog.KEY_GAME ->
                Availability(availableInBuild = true, enabledAtRuntime = settings.embeddedGameEnabled)
            InternalRouteCatalog.KEY_OCR -> Availability(capability.isOcrAvailable(context), enabledAtRuntime = true)
            InternalRouteCatalog.KEY_STREAMS -> Availability(capability.isStreamsAvailable(), enabledAtRuntime = true)
            InternalRouteCatalog.KEY_FAVORITES ->
                Availability(availableInBuild = true, enabledAtRuntime = settings.enableFavorites)
            // Photo only, not video: the panel tile has no per-instance capture-mode config, so it
            // always resolves to the widget's default (photo) capture mode - see AppLaunchPanelRouteIntents.
            InternalRouteCatalog.KEY_QUICK_CAMERA ->
                Availability(
                    availableInBuild = mediaCapabilities.supportsImages,
                    enabledAtRuntime = !settings.disableCameraCapture,
                )
            InternalRouteCatalog.KEY_QUICK_VOICE ->
                Availability(
                    availableInBuild = mediaCapabilities.supportsMicRecording,
                    enabledAtRuntime = settings.micRecordingEnabled,
                )
            InternalRouteCatalog.KEY_SCREEN_RECORDING ->
                Availability(
                    availableInBuild = screenVideoRecordingControllers.isNotEmpty(),
                    enabledAtRuntime = settings.screenRecordingEnabled,
                )
            InternalRouteCatalog.KEY_LINK_DOWNLOAD ->
                Availability(availableInBuild = true, enabledAtRuntime = settings.linkAutoDownloadEnabled)
            // S0978: photo-capture routes gate exactly like KEY_QUICK_CAMERA (images capability + the
            // global camera-capture toggle); the OCR-translate variant additionally needs the translation
            // capability compiled in, and the video route gates on the video capability + video toggle.
            InternalRouteCatalog.KEY_TAKE_PHOTO_SEND_TO,
            InternalRouteCatalog.KEY_TAKE_PHOTO_EDIT ->
                Availability(
                    availableInBuild = mediaCapabilities.supportsImages,
                    enabledAtRuntime = !settings.disableCameraCapture,
                )
            InternalRouteCatalog.KEY_TAKE_PHOTO_OCR_TRANSLATE ->
                Availability(
                    availableInBuild = mediaCapabilities.supportsImages && capability.isTranslationAvailable(),
                    enabledAtRuntime = !settings.disableCameraCapture,
                )
            InternalRouteCatalog.KEY_START_VIDEO_RECORDING ->
                Availability(
                    availableInBuild = mediaCapabilities.supportsVideo,
                    enabledAtRuntime = !settings.disableVideoCapture,
                )
            else -> Availability(availableInBuild = false, enabledAtRuntime = false)
        }
}
