package com.sza.fastmediasorter.ui.cameracapture.model

import androidx.camera.core.AspectRatio

/**
 * S1658: the frame shape the user picked for photo capture, and the one place that expands it into a
 * CameraX stream request plus an optional post-capture crop.
 *
 * The [storedValue] of the first two entries is deliberately the CameraX constant itself, because
 * `camera_aspect_ratio` has held raw `AspectRatio.RATIO_*` values since S1066 - a preference written
 * before this ticket keeps its meaning and no migration runs.
 *
 * [FULL_SCREEN] is not a fourth CameraX ratio, because CameraX offers exactly two. It is the 16:9
 * stream shown filling the host screen and saved cropped to that same shape, so what was visible is
 * what gets saved.
 */
enum class CameraAspectSelection(val storedValue: Int) {

    RATIO_4_3(AspectRatio.RATIO_4_3),
    RATIO_16_9(AspectRatio.RATIO_16_9),
    FULL_SCREEN(FULL_SCREEN_STORED);

    /** The CameraX stream this selection asks the pipeline for. */
    val cameraXAspectRatio: Int
        get() = if (this == RATIO_4_3) AspectRatio.RATIO_4_3 else AspectRatio.RATIO_16_9

    /** True when the saved file must be cropped to the host screen's shape after capture. */
    val cropsToScreen: Boolean
        get() = this == FULL_SCREEN

    /**
     * The selection usable in [videoMode]. Video is recorded through `Recorder`, which returns
     * standard resolutions and offers no place to crop after encoding, so full screen degrades to
     * the 16:9 stream it is built from.
     */
    fun forMode(videoMode: Boolean): CameraAspectSelection =
        if (videoMode && this == FULL_SCREEN) RATIO_16_9 else this

    companion object {

        /** S1658: 16:9 by owner ruling, replacing the 4:3 the photo pipeline used to pin. */
        val DEFAULT = RATIO_16_9

        fun fromStored(value: Int): CameraAspectSelection =
            entries.firstOrNull { it.storedValue == value } ?: DEFAULT

        /**
         * The picker's photo-mode contents: every probed CameraX ratio, plus [FULL_SCREEN] when the
         * device can deliver the 16:9 stream it is built from.
         */
        fun photoOptions(available: List<Int>): List<CameraAspectSelection> {
            val probed = entries.filter { it != FULL_SCREEN && it.storedValue in available }
            return if (AspectRatio.RATIO_16_9 in available) probed + FULL_SCREEN else probed
        }
    }
}

private const val FULL_SCREEN_STORED = 2
