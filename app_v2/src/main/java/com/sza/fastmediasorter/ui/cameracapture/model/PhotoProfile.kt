package com.sza.fastmediasorter.ui.cameracapture.model

import android.hardware.camera2.CameraMetadata

/**
 * S1262: the closed set of photo profiles the capture screen can offer, replacing the single macro
 * button with a menu.
 *
 * Availability is a pure function of the runtime capabilities snapshot - no CameraX or view types
 * here, so the whole set is decidable and testable before anything is bound. A profile that the
 * device cannot honour is never offered rather than offered and silently ignored.
 */
enum class PhotoProfile {
    NORMAL,
    NIGHT,
    PORTRAIT,
    SELFIE,
    MACRO,
    SPORT;

    fun isAvailable(capabilities: CameraRuntimeCapabilities): Boolean = when (this) {
        NORMAL -> true
        NIGHT -> capabilities.supportsNightMode
        PORTRAIT -> capabilities.supportsBokehExtension
        SELFIE -> CameraMetadata.LENS_FACING_FRONT in capabilities.availableLensFacings
        // S1189: most devices implement macro as dedicated optics rather than a focus lock on the
        // main lens, so either signal offers it.
        MACRO -> capabilities.supportsMacro || capabilities.macroLensAvailable
        SPORT -> capabilities.supportsSportExposure()
    }

    private fun CameraRuntimeCapabilities.supportsSportExposure(): Boolean {
        val shutter = shutterRangeNs
        return supportsManualSensor && isoRange != null &&
            shutter != null && shutter.lower <= SPORT_TARGET_EXPOSURE_NS
    }

    companion object {
        /**
         * 1/250 s in nanoseconds - the shortest exposure a sport profile is worth offering at
         * (research 02). A sensor that cannot reach it would produce the same motion blur as NORMAL.
         */
        const val SPORT_TARGET_EXPOSURE_NS = 4_000_000L

        /** Offered profiles in declaration order, so the menu never reshuffles between devices. */
        fun available(capabilities: CameraRuntimeCapabilities): List<PhotoProfile> =
            entries.filter { it.isAvailable(capabilities) }
    }
}
