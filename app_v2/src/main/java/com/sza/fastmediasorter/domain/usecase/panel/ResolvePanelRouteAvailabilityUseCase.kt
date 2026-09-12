package com.sza.fastmediasorter.domain.usecase.panel

import android.content.Context
import android.content.pm.PackageManager
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.screencapture.ScreenVideoRecordingController
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.networkmonitor.NetworkMonitorContract
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
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
    private val networkMonitorContract: NetworkMonitorContract,
    private val screenVideoRecordingControllers: Set<@JvmSuppressWildcards ScreenVideoRecordingController>,
) {

    /**
     * S1924: probed once and held - the hardware cannot appear while the process runs, and the chain
     * is asked for every route on every panel composition. A missing feature and a failed query are
     * the same answer here, which is why the wrapper defaults to false rather than propagating.
     */
    private val hasFrontCamera: Boolean by lazy {
        runCatching { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) }
            .getOrDefault(false)
    }

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

    // S0912: every route's availability lives in this one chain, so a future route cannot silently
    // drift into "insufficient": either it declares its own availableInBuild/enabledAtRuntime pair
    // in [resolveOrNull] or in [resolveWidgetMirrorRoute], or the single fallback below reports it
    // unavailable - there is no second toggle to forget.
    private fun resolve(routeKey: String, settings: AppSettings): Availability =
        resolveOrNull(routeKey, settings) ?: Availability(availableInBuild = false, enabledAtRuntime = false)

    /**
     * S1736: the same chain as [resolve], but null when no branch claims [routeKey] instead of the
     * fabricated unavailable pair.
     *
     * A declared-but-unavailable route and an undeclared one are otherwise the same answer, so a
     * sub-program missing from a surface reads exactly like one the build switched off. The
     * completeness test needs the two apart to assert anything at all.
     */
    @Suppress("CyclomaticComplexMethod") // S2997: one when-branch per route; complexity tracks route count
    fun resolveOrNull(routeKey: String, settings: AppSettings): Availability? =
        when (routeKey) {
            // S1103: the quick-access panel exists in every launcher build and has no runtime toggle.
            InternalRouteCatalog.KEY_APP_LAUNCH_PANEL ->
                Availability(availableInBuild = true, enabledAtRuntime = true)
            // S1856: compiled into every flavor, but the user's own toggle decides whether it launches -
            // the same shape the embedded game and the flashlight use. Hardcoding true here is what let a
            // disabled calculator keep opening from a panel tile and a desktop cell.
            InternalRouteCatalog.KEY_CALCULATOR -> {
                Availability(availableInBuild = true, enabledAtRuntime = settings.enableCalculator)
            }
            // S1411 §6.5: universal across flavors, like the calculator above - only the user's switch
            // gates it, so the compile-time axis stays unconditionally open.
            InternalRouteCatalog.KEY_STOPWATCH ->
                Availability(availableInBuild = true, enabledAtRuntime = settings.enableStopwatch)
            InternalRouteCatalog.KEY_NETWORK_MONITOR ->
                Availability(
                    availableInBuild = networkMonitorContract.isAvailableInBuild,
                    enabledAtRuntime = settings.enableNetworkMonitor,
                )
            InternalRouteCatalog.KEY_GAME ->
                Availability(availableInBuild = true, enabledAtRuntime = settings.embeddedGameEnabled)
            // S1733: the same pair the game uses - compiled into every flavor, gated only by its switch.
            InternalRouteCatalog.KEY_SYSTEM_INFO ->
                Availability(availableInBuild = true, enabledAtRuntime = settings.enableSystemInfo)
            // S2922: Tourist dashboard sub-program - universal across flavors.
            InternalRouteCatalog.KEY_TOURIST_INFO ->
                Availability(availableInBuild = true, enabledAtRuntime = settings.enableTourist).also {
                    Timber.d("S2997: tourist route availability=%s", it)
                }
            // S1883: unlike system information, the companion needs the watch bridge, so it declares the
            // same capability-and-switch pair the quick voice route uses rather than a hardcoded true.
            // S2881: the two listen calls are that bridge in action, so they answer with the same pair -
            // absent where the bridge is not compiled in, off where the owner switched it off.
            InternalRouteCatalog.KEY_WEAR_COMPANION,
            InternalRouteCatalog.KEY_WATCH_LISTEN,
            InternalRouteCatalog.KEY_WATCH_LISTEN_RECORD ->
                Availability(
                    availableInBuild = mediaCapabilities.supportsWearCompanion,
                    enabledAtRuntime = settings.enableWearCompanion,
                )
            // S2673: the runtime axis was a literal `true`, so a panel tile and a desktop cell kept
            // opening the translator with its switch off - the S1856 calculator defect, repeated. The
            // registry entry for this key disables itself through the same field.
            InternalRouteCatalog.KEY_OCR ->
                Availability(
                    availableInBuild = capability.isOcrAvailable(context),
                    enabledAtRuntime = settings.cameraOcrTranslationEnabled,
                )
            InternalRouteCatalog.KEY_STREAMS -> Availability(capability.isStreamsAvailable(), enabledAtRuntime = true)
            InternalRouteCatalog.KEY_FAVORITES ->
                Availability(availableInBuild = true, enabledAtRuntime = settings.enableFavorites)
            // Photo only, not video: the panel tile has no per-instance capture-mode config, so it
            // always resolves to the widget's default (photo) capture mode - see AppLaunchPanelRouteIntents.
            // S2673: the video half was missing, so a build that records video but shows no photos
            // reported this route dead while the programs menu offered it. Both halves are factored
            // into isQuickCaptureEnabled to keep this chain under detekt's cyclomatic ceiling.
            InternalRouteCatalog.KEY_QUICK_CAMERA ->
                Availability(
                    availableInBuild = mediaCapabilities.supportsImages || mediaCapabilities.supportsVideo,
                    enabledAtRuntime = isQuickCaptureEnabled(settings),
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
            // S1924: compiled into every flavor - the camera is gated by a setting, never by a flavor
            // flag, so there is no capability to read here. The three runtime conditions are factored
            // out to keep this chain under detekt's cyclomatic ceiling, which S1883 already reached.
            InternalRouteCatalog.KEY_MIRROR ->
                Availability(availableInBuild = true, enabledAtRuntime = isMirrorEnabled(settings))
            else -> resolveLightRoute(routeKey, settings) ?: resolveCaptureRoute(routeKey, settings)
        }

    /**
     * S2516: the light family - both flashlights, the water flashlight and the black screen - resolved
     * apart from the chain above.
     *
     * They moved out because adding the water flashlight took that chain to detekt's cyclomatic
     * ceiling, which S1883 had already reached and S1924 had already stepped back from once. The family
     * is the natural seam: each of these four routes paints its own window and none of them reads a
     * capability except the physical torch, which needs the hardware.
     *
     * Null when [routeKey] is not one of them, so the caller falls through to its own next branch.
     */
    private fun resolveLightRoute(routeKey: String, settings: AppSettings): Availability? =
        when (routeKey) {
            // S1796: the flashlight needs no capability - it only paints its own window - so the pair
            // is the same shape the embedded game uses: always built in, gated by the user's toggle.
            InternalRouteCatalog.KEY_FRONT_FLASHLIGHT ->
                Availability(availableInBuild = true, enabledAtRuntime = settings.frontFlashlightEnabled)
            InternalRouteCatalog.KEY_PHYSICAL_FLASHLIGHT ->
                Availability(
                    availableInBuild = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
                    enabledAtRuntime = true,
                )
            // S2516: deliberately NOT gated on a camera flash, unlike the entry above. Half of this
            // program is the lit screen, which every device has; on a phone without a flash it is still
            // a light and still a lock, and the torch call degrades to a logged no-op.
            InternalRouteCatalog.KEY_WATER_FLASHLIGHT ->
                Availability(availableInBuild = true, enabledAtRuntime = settings.waterFlashlightEnabled)
            // S2211: black screen needs no special capability - always available in build and runtime.
            InternalRouteCatalog.KEY_BLACK_SCREEN ->
                Availability(availableInBuild = true, enabledAtRuntime = true)
            else -> null
        }

    /**
     * S2673: the quick-capture route is one program with two capture modes, so either mode being
     * both supported and switched on keeps it alive - the pair the programs menu has always used.
     */
    private fun isQuickCaptureEnabled(settings: AppSettings): Boolean =
        (mediaCapabilities.supportsImages && !settings.disableCameraCapture) ||
            (mediaCapabilities.supportsVideo && !settings.disableVideoCapture)

    /**
     * S1924: the mirror needs its own switch on, the global camera switch not off, and a front lens
     * to point at - strategic §3.2 and §6.3. Any one of the three missing makes the route dead.
     */
    private fun isMirrorEnabled(settings: AppSettings): Boolean =
        settings.mirrorEnabled && !settings.disableCameraCapture && hasFrontCamera

    /**
     * S0978: the four capture routes - two photo shortcuts, the OCR-translate variant and the video one.
     *
     * Split out of [resolveOrNull] for the same reason [resolveWidgetMirrorRoute] was (S1883 pushed the
     * chain back to detekt's cyclomatic ceiling when the Wear companion joined it). The closed-set
     * contract is unchanged: this function ends in the widget-mirror chain, which ends in the same
     * "no branch claims this route" answer the single chain used to hold.
     *
     * All four gate on the global camera-capture toggle rather than a per-route one; the OCR-translate
     * variant additionally needs the translation capability compiled in, and the video route reads the
     * video capability and the video toggle instead of their photo counterparts.
     */
    private fun resolveCaptureRoute(routeKey: String, settings: AppSettings): Availability? =
        when (routeKey) {
            InternalRouteCatalog.KEY_TAKE_PHOTO_SEND_TO,
            InternalRouteCatalog.KEY_TAKE_PHOTO_EDIT ->
                Availability(
                    availableInBuild = mediaCapabilities.supportsImages,
                    enabledAtRuntime = !settings.disableCameraCapture,
                )
            InternalRouteCatalog.KEY_TAKE_PHOTO_OCR_TRANSLATE ->
                Availability(
                    availableInBuild = mediaCapabilities.supportsImages && capability.isTranslationAvailable(context),
                    enabledAtRuntime = !settings.disableCameraCapture,
                )
            InternalRouteCatalog.KEY_START_VIDEO_RECORDING ->
                Availability(
                    availableInBuild = mediaCapabilities.supportsVideo,
                    enabledAtRuntime = !settings.disableVideoCapture,
                )
            else -> resolveWidgetMirrorRoute(routeKey, settings)
        }

    /**
     * S1170: the five routes that exist to mirror a home-screen widget's own tap destination.
     *
     * Split out of [resolveOrNull] only to stay under detekt's cyclomatic ceiling - the closed-set
     * contract above is unchanged, because this function ends in the same unclaimed-route answer that
     * [resolveOrNull] used to hold. Each gate is the one its widget provider already applies, so
     * a desktop cell and the same widget on the Android home screen agree on when the destination is
     * dead. Without these branches the default would swallow all five and every launcher gadget built on
     * them would silently do nothing.
     */
    private fun resolveWidgetMirrorRoute(routeKey: String, settings: AppSettings): Availability? =
        when (routeKey) {
            InternalRouteCatalog.KEY_CAMERA_PHOTOS ->
                Availability(availableInBuild = mediaCapabilities.supportsImages, enabledAtRuntime = true)
            // The camera-launch trampoline opens whichever of photo/video is live, so it is dead only when
            // BOTH are - hence the disjunction rather than the images-only gate its siblings use.
            InternalRouteCatalog.KEY_CAMERA_LAUNCH ->
                Availability(
                    availableInBuild = mediaCapabilities.supportsImages || mediaCapabilities.supportsVideo,
                    enabledAtRuntime = (mediaCapabilities.supportsImages && !settings.disableCameraCapture) ||
                        (mediaCapabilities.supportsVideo && !settings.disableVideoCapture),
                )
            InternalRouteCatalog.KEY_CONTINUE_READING ->
                Availability(availableInBuild = true, enabledAtRuntime = true)
            InternalRouteCatalog.KEY_RANDOM_MUSIC ->
                Availability(availableInBuild = mediaCapabilities.supportsAudio, enabledAtRuntime = true)
            InternalRouteCatalog.KEY_SCHEDULED_TASKS ->
                Availability(availableInBuild = true, enabledAtRuntime = settings.enableScheduledOperations)
            else -> null
        }
}
