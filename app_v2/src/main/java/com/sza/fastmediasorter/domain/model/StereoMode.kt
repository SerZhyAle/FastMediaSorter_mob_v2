package com.sza.fastmediasorter.domain.model

/**
 * Stereoscopic / spherical display mode for a piece of media.
 *
 * Persisted as a String value in SharedPreferences (name of the enum constant).
 * Used by StereoDetector (auto-detect) and by the user via the STEREO tab
 * in PlaybackControlDialogFragment.
 *
 * Two families of modes:
 *  - **Plat (flat)**: [SBS_FULL], [SBS_HALF], [OU], [MONO] — rendered via per-eye UV crop
 *    into an OpenXR projection layer (or 2D passthrough in non-VR flavors).
 *  - **Spherical / panoramic**: [EQUIRECT_360_MONO], [EQUIRECT_360_SBS], [EQUIRECT_360_OU],
 *    [EQUIRECT_180_MONO], [EQUIRECT_180_SBS], [VR180_FISHEYE_SBS], [CYLINDER_180] —
 *    rendered via Equirect2 / Cylinder composition layers in the vr flavor; the standard
 *    flavor surfaces a "VR edition" install CTA instead.
 */
enum class StereoMode {
    /**
     * Let the app decide: run StereoDetector on each video.
     * Stored preference value — runtime detection result is one of the concrete modes below.
     */
    AUTO,

    // ── Flat stereo (per-eye UV crop) ─────────────────────────────────────

    /**
     * Side-by-Side horizontal stereo, full-width.
     * Left 50% of the frame → left eye; right 50% → right eye.
     * Typical aspect ratio ≈ 32:9 (e.g., 3840×1080).
     */
    SBS_FULL,

    /**
     * Side-by-Side with anamorphic (squeezed) encoding.
     * Used in some 3D Blu-ray rips at normal 16:9 AR with tall height (≥1800 px).
     */
    SBS_HALF,

    /**
     * Over-Under (Top-and-Bottom) vertical stereo.
     * Top 50% → left eye; bottom 50% → right eye.
     */
    OU,

    /**
     * Standard monoscopic (no stereo).
     * Default state; renderer passes frames through unchanged.
     */
    MONO,

    // ── Spherical / panoramic (OpenXR composition layers) ─────────────────

    /**
     * 360° equirectangular monoscopic. Single full-sphere projection.
     * Typical AR 2:1 (e.g., 4096×2048, 7680×3840).
     */
    EQUIRECT_360_MONO,

    /**
     * 360° equirectangular stereoscopic, Side-by-Side layout.
     * Left half → left eye sphere; right half → right eye sphere.
     * Typical AR 4:1 (e.g., 7680×1920) or 2:1 per eye stacked SBS.
     */
    EQUIRECT_360_SBS,

    /**
     * 360° equirectangular stereoscopic, Over-Under layout.
     * Top half → left eye sphere; bottom half → right eye sphere.
     * Typical AR 1:1 (e.g., 3840×3840) for true full-sphere OU.
     */
    EQUIRECT_360_OU,

    /**
     * 180° equirectangular monoscopic (front hemisphere only).
     * Typical AR 1:1 (single eye covers half-sphere).
     */
    EQUIRECT_180_MONO,

    /**
     * 180° equirectangular stereoscopic, Side-by-Side layout.
     * Left half → left eye half-sphere; right half → right eye half-sphere.
     * Typical AR 2:1 with each eye covering the front hemisphere.
     */
    EQUIRECT_180_SBS,

    /**
     * VR180 fisheye stereoscopic, Side-by-Side layout (Google VR180 convention).
     * Left/right halves carry fisheye-projected 180° captures.
     * Renderer dewarps to equirect or feeds the Meta runtime as an Equirect2 layer
     * depending on platform support.
     */
    VR180_FISHEYE_SBS,

    /**
     * 180° cylinder-projected panoramic (seated flat panorama wrap).
     * Renders as an OpenXR CylinderKHR composition layer.
     * Typical AR 2:1 with non-spherical projection (no top/bottom poles).
     */
    CYLINDER_180,

    // ── Sentinel ──────────────────────────────────────────────────────────

    /**
     * Sentinel used internally by StereoDetector when neither metadata
     * nor aspect ratio provides a conclusive result.
     * MUST NOT be stored to preferences or passed to the renderer.
     */
    UNKNOWN;

    /**
     * True for [EQUIRECT_*], [VR180_FISHEYE_SBS], and [CYLINDER_180] modes —
     * anything that needs a curved composition layer.
     */
    fun isSpherical(): Boolean = when (this) {
        EQUIRECT_360_MONO,
        EQUIRECT_360_SBS,
        EQUIRECT_360_OU,
        EQUIRECT_180_MONO,
        EQUIRECT_180_SBS,
        VR180_FISHEYE_SBS,
        CYLINDER_180 -> true
        else -> false
    }

    /**
     * True when both eyes receive different pixels (stereoscopic perception).
     * Covers flat SBS/OU and spherical SBS/OU/fisheye variants.
     */
    fun isStereoscopic(): Boolean = when (this) {
        SBS_FULL,
        SBS_HALF,
        OU,
        EQUIRECT_360_SBS,
        EQUIRECT_360_OU,
        EQUIRECT_180_SBS,
        VR180_FISHEYE_SBS -> true
        else -> false
    }

    /**
     * True for modes limited to the front hemisphere (180°) instead of full 360°.
     * Useful when building OpenXR layers to crop UV bounds.
     */
    fun is180Only(): Boolean = when (this) {
        EQUIRECT_180_MONO,
        EQUIRECT_180_SBS,
        VR180_FISHEYE_SBS,
        CYLINDER_180 -> true
        else -> false
    }

    companion object {
        /** Safely deserialize from preferences string; falls back to AUTO on unknown value. */
        fun fromKey(key: String?): StereoMode =
            entries.firstOrNull { it.name == key } ?: AUTO
    }
}
