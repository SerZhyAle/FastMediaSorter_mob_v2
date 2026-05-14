package com.sza.fastmediasorter.ui.player.render.stereoscopic

import com.sza.fastmediasorter.domain.model.StereoMode

interface VrLayerFactory {
    fun describe(stereo: StereoMode, renderingMode: VrRenderingMode): VrLayerDescriptor
}

/**
 * Runtime rendering-mode preference used by the VR player.
 *
 * Older settings stored FULL_SBS/FULL_OU while the dialog now stores FULL_STEREO.
 * The parser accepts both so Phase 4 can consume either source without forcing migration.
 */
enum class VrRenderingMode {
    CINEMA,
    FULL_STEREO;

    companion object {
        fun fromPreferenceValue(value: String?): VrRenderingMode = when (value) {
            "FULL_STEREO", "FULL_SBS", "FULL_OU" -> FULL_STEREO
            else -> CINEMA
        }
    }
}