package com.sza.fastmediasorter.vr.helpers

import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.vr.VrLaunchRoute
import timber.log.Timber

internal data class VrRouteDecision(
    val route: VrLaunchRoute,
    val effectiveStereoMode: StereoMode,
    val logReason: String,
    @StringRes val userMessageResId: Int? = null,
) {
    /**
     * S0018 atomic logging: emits the (route, reason) pair from the same decision instance,
     * so route and reason can never appear desynchronised in the log. Replaces the
     * format string previously inlined in VrPlayerActivity.buildRouteDecision.
     */
    fun logTo(currentFile: MediaFile, requestedStereoMode: StereoMode, autoDetect: Boolean) {
        Timber.i(
            "VrPlayerActivity: route decision file=%s type=%s requested=%s effective=%s autoDetect=%b route=%s reason=%s",
            currentFile.path,
            currentFile.type,
            requestedStereoMode,
            effectiveStereoMode,
            autoDetect,
            route,
            logReason,
        )
    }
}

/**
 * Centralizes VR route selection so VrPlayerActivity remains a coordinator.
 *
 * The helper decides whether a file should stay in panel mode, enter immersive playback, or
 * fall back with an explicit user-visible message when the file asks for immersive playback but
 * the current media family is not supported by the headset renderer stack yet.
 *
 * S0018 invariant: route assignments live only inside VrRouteDecisionHelper.decide.
 * No code outside this class may construct or mutate a VrRouteDecision.route value;
 * downstream consumers may only READ routeDecision.route (e.g. in `when` arms).
 */
internal class VrRouteDecisionHelper {

    fun decide(
        currentFile: MediaFile,
        effectiveStereoMode: StereoMode,
        settings: AppSettings,
        userForcedImmersive: Boolean = false,
        userForcedPanel: Boolean = false,
    ): VrRouteDecision {
        if (settings.disable3dVr) {
            return VrRouteDecision(
                route = VrLaunchRoute.STANDARD_PANEL_FALLBACK,
                effectiveStereoMode = StereoMode.MONO,
                logReason = "disable-3d-vr",
            )
        }

        // WHY: When the user explicitly requests panel mode (e.g. via the 3DVR toggle from
        // immersive), honour it unconditionally — even for spherical/stereoscopic content that
        // would otherwise be auto-routed to IMMERSIVE_VIDEO. Without this flag the panel toggle
        // had no effect on VR180/360 files because the route decision always overrode the intent.
        if (userForcedPanel) {
            return VrRouteDecision(
                route = VrLaunchRoute.STANDARD_PANEL_FALLBACK,
                effectiveStereoMode = effectiveStereoMode,
                logReason = "user-forced-panel",
            )
        }

        // When the user explicitly tapped "3DVR", bypass stereo-detection gate and force
        // immersive route for video. disable3dVr already bailed out above, so this is safe.
        if (userForcedImmersive && currentFile.type == MediaType.VIDEO) {
            // WHY: for flat 2D content keep MONO — the cinema quad layer already provides
            // an immersive viewing experience without faking SBS_FULL, which is unsupported
            // in CINEMA rendering mode and causes DefaultVrLayerFactory to emit a warning.
            val forcedMode = if (effectiveStereoMode.isStereoscopic() || effectiveStereoMode.isSpherical()) {
                effectiveStereoMode
            } else {
                StereoMode.MONO  // 2D content plays in the immersive cinema quad, not fake SBS
            }
            return VrRouteDecision(
                route = VrLaunchRoute.IMMERSIVE_VIDEO,
                effectiveStereoMode = forcedMode,
                logReason = "user-forced-immersive",
            )
        }

        // Quest panel mode is monoscopic — it shows the same pixels to both eyes, so flat SBS/OU
        // cannot produce a stereo effect there. Any stereoscopic OR spherical content therefore
        // has to enter immersive XR to actually expose per-eye pixels. Only plain 2D falls back
        // to the panel player.
        val requestsImmersive = effectiveStereoMode.isSpherical() || effectiveStereoMode.isStereoscopic()

        // Auto-immersive setting (spec_vr-auto-immersive-setting): when the user disabled the
        // automatic transition, stereo content stays on the flat screen unless explicitly forced.
        // userForcedImmersive bypass already covers the "manual 3DVR button" path.
        if (requestsImmersive && !userForcedImmersive && !settings.vrAutoImmersive) {
            return VrRouteDecision(
                route = VrLaunchRoute.STANDARD_PANEL_FALLBACK,
                effectiveStereoMode = effectiveStereoMode,
                logReason = "auto-immersive-disabled",
            )
        }

        if (!requestsImmersive) {
            if (currentFile.type == MediaType.VIDEO) {
                val fallbackRoute = if (settings.vrAutoImmersive) {
                    VrLaunchRoute.CINEMA_IMMERSIVE
                } else {
                    VrLaunchRoute.STANDARD_PANEL_FALLBACK
                }
                return VrRouteDecision(
                    route = fallbackRoute,
                    effectiveStereoMode = StereoMode.MONO,
                    logReason = "plain-2d-video",
                )
            }
            return VrRouteDecision(
                route = VrLaunchRoute.STANDARD_PANEL_FALLBACK,
                effectiveStereoMode = effectiveStereoMode,
                logReason = "plain-2d-content",
            )
        }

        return when (currentFile.type) {
            MediaType.VIDEO -> VrRouteDecision(
                route = VrLaunchRoute.IMMERSIVE_VIDEO,
                effectiveStereoMode = effectiveStereoMode,
                logReason = "immersive-video",
            )

            MediaType.IMAGE -> VrRouteDecision(
                route = VrLaunchRoute.IMMERSIVE_STATIC_IMAGE,
                effectiveStereoMode = effectiveStereoMode,
                logReason = "immersive-static-image",
            )

            else -> VrRouteDecision(
                route = VrLaunchRoute.UNSUPPORTED_IMMERSIVE_WITH_MESSAGE,
                effectiveStereoMode = effectiveStereoMode,
                logReason = "unsupported-immersive-media-type",
                userMessageResId = R.string.vr_immersive_unsupported_media,
            )
        }
    }
}