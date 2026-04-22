package com.sza.fastmediasorter.vr.helpers

import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.vr.VrLaunchRoute

internal data class VrRouteDecision(
    val route: VrLaunchRoute,
    val effectiveStereoMode: StereoMode,
    val logReason: String,
    @StringRes val userMessageResId: Int? = null,
)

/**
 * Centralizes VR route selection so VrPlayerActivity remains a coordinator.
 *
 * The helper decides whether a file should stay in panel mode, enter immersive playback, or
 * fall back with an explicit user-visible message when the file asks for immersive playback but
 * the current media family is not supported by the headset renderer stack yet.
 */
internal class VrRouteDecisionHelper {

    fun decide(
        currentFile: MediaFile,
        effectiveStereoMode: StereoMode,
        settings: AppSettings,
    ): VrRouteDecision {
        if (settings.disable3dVr) {
            return VrRouteDecision(
                route = VrLaunchRoute.STANDARD_PANEL_FALLBACK,
                effectiveStereoMode = StereoMode.MONO,
                logReason = "disable-3d-vr",
            )
        }

        // Quest panel mode is monoscopic — it shows the same pixels to both eyes, so flat SBS/OU
        // cannot produce a stereo effect there. Any stereoscopic OR spherical content therefore
        // has to enter immersive XR to actually expose per-eye pixels. Only plain 2D falls back
        // to the panel player.
        val requestsImmersive = effectiveStereoMode.isSpherical() || effectiveStereoMode.isStereoscopic()
        if (!requestsImmersive) {
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