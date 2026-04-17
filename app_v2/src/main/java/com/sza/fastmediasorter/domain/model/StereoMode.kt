package com.sza.fastmediasorter.domain.model

/**
 * Stereoscopic video display mode.
 *
 * Persisted as a String value in SharedPreferences (name of the enum constant).
 * Used by StereoDetector (auto-detect) and by the user via the "3D" tab
 * in PlaybackSettingsDialog.
 */
enum class StereoMode {
    /**
     * Let the app decide: run StereoDetector on each video.
     * Stored preference value — runtime detection result is in StereoMode.SBS_FULL / OU / MONO.
     */
    AUTO,

    /**
     * Side-by-Side horizontal stereo.
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
     * Deferred to v2.64+; enum value reserved so preferences don't crash on upgrade.
     */
    OU,

    /**
     * Standard monoscopic (no stereo).
     * Default state; renderer passes frames through unchanged.
     */
    MONO,

    /**
     * Sentinel used internally by StereoDetector when neither metadata
     * nor aspect ratio provides a conclusive result.
     * MUST NOT be stored to preferences or passed to the renderer.
     */
    UNKNOWN;

    companion object {
        /** Safely deserialize from preferences string; falls back to AUTO on unknown value. */
        fun fromKey(key: String?): StereoMode =
            entries.firstOrNull { it.name == key } ?: AUTO
    }
}
