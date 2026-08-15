package com.sza.fastmediasorter.core.cast

import com.sza.fastmediasorter.domain.model.StereoMode

/**
 * Which half of a stereo frame the Cast receiver should be shown (S1558).
 *
 * The panel and the Cast path must show the same eye, so this is a request the player hands over
 * the [CastController] seam rather than something the Cast path re-derives. Re-deriving it would
 * diverge on any file carrying a per-file stereo override or Matroska stereo metadata, neither of
 * which the dimension heuristic sees.
 */
enum class CastStereoCrop {
    /** Keep the right half of a side-by-side frame. */
    RIGHT_HALF,

    /** Keep the bottom half of an over-under frame. */
    BOTTOM_HALF,
}

/**
 * Maps a stereo mode to the half the panel is already showing, or `null` when the panel shows the
 * whole frame.
 *
 * Mirrors `ui.player.helpers.PanelStereoCropApplier.buildMatrixFor`, which is the origin of this
 * mapping: it scales the backing `TextureView` about `w * 0.75` for every side-by-side mode (the
 * right half) and about `h * 0.75` for every over-under mode (the bottom half). A stereo mode added
 * there has to be added here in the same pass, or Cast output silently keeps both eyes for it.
 */
fun StereoMode.toCastStereoCrop(): CastStereoCrop? = when (this) {
    StereoMode.SBS_FULL,
    StereoMode.SBS_HALF,
    StereoMode.EQUIRECT_360_SBS,
    StereoMode.EQUIRECT_180_SBS,
    StereoMode.VR180_FISHEYE_SBS -> CastStereoCrop.RIGHT_HALF

    StereoMode.OU,
    StereoMode.EQUIRECT_360_OU -> CastStereoCrop.BOTTOM_HALF

    else -> null
}
