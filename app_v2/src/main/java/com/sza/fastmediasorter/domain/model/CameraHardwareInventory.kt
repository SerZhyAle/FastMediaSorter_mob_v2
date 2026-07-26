package com.sza.fastmediasorter.domain.model

import android.util.Range
import android.util.Size

/**
 * What the device reports about its cameras, read once for the System info report.
 *
 * Kept free of Camera2 types so the diagnostics section can be formatted and reasoned about without
 * holding a camera handle: the raw characteristic constants travel as plain ints and are resolved to
 * text at render time.
 */
data class CameraHardwareInventory(
    val entries: List<CameraHardwareEntry> = emptyList(),
)

/**
 * One camera the platform declares, or one physical sub-lens of such a camera.
 *
 * Every field except [cameraId] is nullable on purpose: a device may refuse any individual
 * characteristic, and a refused value must stay distinguishable from a real zero (a fixed-focus lens
 * genuinely reports 0 diopters).
 */
data class CameraHardwareEntry(
    val cameraId: String,
    val logicalParentId: String? = null,
    val lensFacing: Int? = null,
    val focalLengthsMm: List<Float> = emptyList(),
    val zoomRange: Range<Float>? = null,
    val minFocusDistanceDiopters: Float? = null,
    val focusDistanceCalibration: Int? = null,
    val hardwareLevel: Int? = null,
    val maxJpegSize: Size? = null,
    val maxHighResolutionJpegSize: Size? = null,
) {
    /** The entry describes a lens inside a logical camera rather than a camera the app can list. */
    val isPhysicalSubLens: Boolean get() = logicalParentId != null
}
