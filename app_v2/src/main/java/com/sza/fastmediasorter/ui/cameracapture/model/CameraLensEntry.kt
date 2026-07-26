package com.sza.fastmediasorter.ui.cameracapture.model

import androidx.camera.core.CameraInfo

/**
 * One lens the capture screen can offer, which is not the same thing as one camera the platform
 * lists: a device fuses ultra-wide, main and macro optics into a single logical camera, and those
 * sub-lenses are reachable only by naming them explicitly when binding.
 *
 * [cameraInfo] is always the **logical** camera to select; [physicalCameraId] narrows that selection
 * to one lens inside it. Describes a lens only - operating one belongs to the session manager
 * (strategic ADR-1).
 */
data class CameraLensEntry(
    val cameraInfo: CameraInfo,
    val logicalCameraId: String,
    val physicalCameraId: String? = null,
    val lensFacing: Int,
    val focalLengthMm: Float = 0f,
    val minZoomRatio: Float = CameraRuntimeCapabilities.DEFAULT_ZOOM,
    val minFocusDistanceDiopters: Float = 0f,
    /** The lens reaches a magnification no sibling of the same facing already covers. */
    val hasOwnMagnification: Boolean = true,
) {
    /** The entry names a lens inside a logical camera rather than the logical camera itself. */
    val isPhysicalSubLens: Boolean get() = physicalCameraId != null

    /** Stable identity for persisting the user's lens choice across sessions. */
    val id: String get() = physicalCameraId?.let { "$logicalCameraId/$it" } ?: logicalCameraId
}
