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
    /** S1261: sensor physical width in mm for FOV-normalized equivalents; 0 = unknown. */
    val sensorWidthMm: Float = 0f,
    /**
     * S1261: zoom floor of the logical camera this lens binds through - its own floor for a logical
     * entry, the parent's for a physical sub-lens. Below 1 exactly when the platform fuses a wider
     * lens behind this camera (the S25 FE 0.57 the raw focal ratio could never produce).
     */
    val parentLogicalMinZoom: Float = 0f,
    /** S1261: native ratio -> equivalent zoom factor; filled by the enumeration post-pass. */
    val equivalentMultiplier: Float = CameraRuntimeCapabilities.DEFAULT_ZOOM,
) {
    /** The entry names a lens inside a logical camera rather than the logical camera itself. */
    val isPhysicalSubLens: Boolean get() = physicalCameraId != null

    /** Stable identity for persisting the user's lens choice across sessions. */
    val id: String get() = physicalCameraId?.let { "$logicalCameraId/$it" } ?: logicalCameraId
}
