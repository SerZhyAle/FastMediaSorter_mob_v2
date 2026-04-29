package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode

/**
 * S0026: pure routing decision for BrowseEventHandler.
 *
 * Mirrors the subset of [com.sza.fastmediasorter.vr.helpers.VrRouteDecisionHelper] rules that
 * Browse can honor BEFORE launching VrPlayerActivity, so stereo files with auto-immersive
 * disabled never go through the "VR window flicker" path. The inner helper remains the
 * source of truth once VR has been entered (e.g. via deep-link or 3DVR button).
 */
internal object BrowseRoutingDecision {

    enum class Route { STANDARD_PLAYER, VR_PLAYER }

    fun decide(
        file: MediaFile,
        effectiveStereoMode: StereoMode,
        settings: AppSettings,
    ): Route {
        if (file.type != MediaType.VIDEO) return Route.STANDARD_PLAYER
        if (settings.disable3dVr) return Route.STANDARD_PLAYER

        val isImmersiveContent =
            effectiveStereoMode.isStereoscopic() || effectiveStereoMode.isSpherical()
        if (!isImmersiveContent) return Route.STANDARD_PLAYER

        // Mirror of VrRouteDecisionHelper "auto-immersive-disabled" branch: stereo content
        // stays on the standard panel when the user disabled the toggle. Without this gate
        // BrowseEventHandler routes the file into VrPlayerActivity, the inner helper fires
        // STANDARD_PANEL_FALLBACK on its own, and the user sees a VR-window flicker.
        if (!settings.vrAutoImmersive) return Route.STANDARD_PLAYER

        return Route.VR_PLAYER
    }
}
