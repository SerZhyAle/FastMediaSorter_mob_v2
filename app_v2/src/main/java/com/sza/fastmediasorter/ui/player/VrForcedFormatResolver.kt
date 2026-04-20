package com.sza.fastmediasorter.ui.player

import com.sza.fastmediasorter.domain.model.StereoMode

/**
 * Resolves family-specific VR forced-format settings against the detected stereo mode.
 *
 * Phase 8 split the old global vrForcedFormat into flat and spherical settings so a
 * forced flat fallback cannot accidentally override 360°/VR180 media, and vice versa.
 */
internal object VrForcedFormatResolver {

    fun resolve(
        detected: StereoMode,
        perFileOverride: StereoMode?,
        forcedPlat: StereoMode?,
        forcedSpherical: StereoMode?,
    ): StereoMode {
        val rememberedMode = perFileOverride
            ?.takeUnless { it == StereoMode.AUTO || it == StereoMode.UNKNOWN }
        if (rememberedMode != null) return rememberedMode

        if (detected == StereoMode.AUTO || detected == StereoMode.UNKNOWN) return detected

        val forcedForFamily = if (detected.isSpherical()) forcedSpherical else forcedPlat
        return forcedForFamily
            ?.takeUnless { it == StereoMode.AUTO || it == StereoMode.UNKNOWN }
            ?: detected
    }

    fun mapPlatSetting(key: String?): StereoMode = when (key?.uppercase()) {
        "AUTO", null -> StereoMode.AUTO
        "SBS" -> StereoMode.SBS_FULL
        "OU" -> StereoMode.OU
        "MONO" -> StereoMode.MONO
        else -> StereoMode.AUTO
    }

    fun mapSphericalSetting(key: String?): StereoMode {
        if (key.isNullOrBlank() || key.equals("AUTO", ignoreCase = true)) return StereoMode.AUTO
        val mapped = StereoMode.fromKey(key)
        return mapped.takeIf { it.isSpherical() } ?: StereoMode.AUTO
    }
}