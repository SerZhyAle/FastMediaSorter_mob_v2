package com.sza.fastmediasorter.ui.xr.helpers

import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.StereoDetector
import timber.log.Timber

enum class ProjectionType(val value: Int) {
    SPHERE_360(0),
    HEMISPHERE_180(1),
    FLAT(2)
}

enum class StereoLayout(val value: Int) {
    MONO(0),
    TOP_BOTTOM(1),
    SIDE_BY_SIDE(2)
}

data class RenderConfig(
    val projection: ProjectionType,
    val layout: StereoLayout
)

/**
 * S0989: filename -> projection/layout resolution for the immersive renderer, extracted from
 * DiagnosticXrActivity. Wraps the shared [StereoDetector] and falls back to the legacy token scan.
 */
class VrStereoConfigResolver(private val stereoDetector: StereoDetector) {

    fun resolve(filename: String): RenderConfig {
        val name = filename.lowercase()

        // S0771: route through StereoDetector first so the immersive renderer and the 2D panel agree
        // on stereo layout. The prior local-only parser lacked the 3dh/3dv/tab/hou tokens and rendered
        // side-by-side films (e.g. *_180x180_3dh.mp4) as MONO. UNKNOWN falls through to the legacy
        // token scan below so no previously-recognised name regresses.
        val detected = stereoDetector.detectFromFilename(filename).toRenderConfigOrNull()
        val config = detected ?: RenderConfig(legacyProjection(name), legacyLayout(name))
        Timber.d(
            "parseFilenameConfig: $filename -> projection=${config.projection}, layout=${config.layout} " +
                "(source=${if (detected != null) "stereo-detector" else "legacy"})"
        )
        // S1112: verify TB (_stereo_tb) content resolves to TOP_BOTTOM, not SIDE_BY_SIDE.
        return config
    }

    /**
     * Legacy projection token scan, kept as the fallback when [StereoDetector] returns UNKNOWN.
     * Extracted from [resolve] so that method stays under the complexity threshold - behaviour is
     * byte-for-byte the conditions it had inline.
     */
    private fun legacyProjection(name: String): ProjectionType = when {
        name.contains("_360") || name.contains("360_") -> ProjectionType.SPHERE_360
        name.contains("_180") || name.contains("180_") -> ProjectionType.HEMISPHERE_180
        name.contains("_flat") || name.contains("flat_") -> ProjectionType.FLAT
        name.contains("panorama") || name.contains("panoramic") ||
            name.contains("equirectangular") -> ProjectionType.SPHERE_360
        else -> ProjectionType.FLAT
    }

    /**
     * Legacy layout token scan.
     *
     * S0290 (owner feedback 2026-05-22): SPECIFIC markers (_sbs, _tb, _lr, _ou) MUST be checked
     * BEFORE the generic `_stereo` fallback. The old order matched `_stereo` first and routed
     * `video_360_stereo_sbs.mp4` to TOP_BOTTOM. Order: SBS family, then TB family, then explicit
     * MONO marker, then generic `_stereo` defaults to TB.
     */
    private fun legacyLayout(name: String): StereoLayout = when {
        name.contains("_sbs") || name.contains("_sidebyside") || name.contains("_hsbs") ||
            name.contains("_fsbs") || name.contains("_lr") || name.contains("_rl") ->
            StereoLayout.SIDE_BY_SIDE
        name.contains("_tb") || name.contains("_topbottom") || name.contains("_ou") ||
            name.contains("_overunder") || name.contains("stereo_tb") ->
            StereoLayout.TOP_BOTTOM
        // Explicit mono marker wins over the generic `_stereo` fallback below.
        name.contains("_mono") || name.contains("mono_") -> StereoLayout.MONO
        // Generic `_stereo` with no specific layout marker defaults to TB (industry default).
        name.contains("_stereo") || name.contains("stereo") -> StereoLayout.TOP_BOTTOM
        else -> StereoLayout.MONO
    }

    /**
     * S1217: what the container itself says about a local MP4, read from its `st3d`/`sv3d` boxes.
     *
     * Null when the path is not an MP4, carries no spatial boxes, or they are inconclusive - which is
     * the common case for a site rip, so this is an addition to the filename scan and never a
     * replacement for it. Parses the file, so it must not run on the main thread; the caller owns
     * that. [resolve] stays synchronous and pure precisely so the session-ready path can call it.
     */
    fun resolveFromMetadata(localPath: String): RenderConfig? =
        stereoDetector.detectFromMp4Path(localPath).toRenderConfigOrNull()

    /**
     * S0771: map the shared [StereoDetector] verdict onto the immersive renderer's projection/layout
     * pair. Returns null for [StereoMode.UNKNOWN] so the caller keeps the legacy filename scan. The XR
     * projection enum has no fisheye type, so [StereoMode.VR180_FISHEYE_SBS] uses [ProjectionType.HEMISPHERE_180]
     * (equirect hemisphere) - the same projection the legacy parser already chose for 180 content; only
     * the stereo layout is corrected here.
     */
    private fun StereoMode.toRenderConfigOrNull(): RenderConfig? = when (this) {
        StereoMode.VR180_FISHEYE_SBS,
        StereoMode.EQUIRECT_180_SBS -> RenderConfig(ProjectionType.HEMISPHERE_180, StereoLayout.SIDE_BY_SIDE)
        StereoMode.EQUIRECT_180_MONO,
        StereoMode.CYLINDER_180 -> RenderConfig(ProjectionType.HEMISPHERE_180, StereoLayout.MONO)
        StereoMode.EQUIRECT_360_SBS -> RenderConfig(ProjectionType.SPHERE_360, StereoLayout.SIDE_BY_SIDE)
        StereoMode.EQUIRECT_360_OU -> RenderConfig(ProjectionType.SPHERE_360, StereoLayout.TOP_BOTTOM)
        StereoMode.EQUIRECT_360_MONO -> RenderConfig(ProjectionType.SPHERE_360, StereoLayout.MONO)
        StereoMode.SBS_FULL,
        StereoMode.SBS_HALF -> RenderConfig(ProjectionType.FLAT, StereoLayout.SIDE_BY_SIDE)
        StereoMode.OU -> RenderConfig(ProjectionType.FLAT, StereoLayout.TOP_BOTTOM)
        StereoMode.MONO -> RenderConfig(ProjectionType.FLAT, StereoLayout.MONO)
        // AUTO/UNKNOWN are inconclusive - defer to the legacy filename scan in the caller.
        StereoMode.AUTO,
        StereoMode.UNKNOWN -> null
    }
}
