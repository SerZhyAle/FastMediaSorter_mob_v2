package com.sza.fastmediasorter.ui.cameracapture.helpers

import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import com.sza.fastmediasorter.ui.cameracapture.model.PhotoProfile

/**
 * S1262: the sport recipe, as a pure builder.
 *
 * It lives outside [CameraCaptureSessionManager] because that class is at its `TooManyFunctions`
 * ceiling and because the interesting part - clamping a target exposure and ISO into whatever
 * ranges the sensor actually reports - is decidable without a camera and therefore testable.
 *
 * The recipe itself is research 02's: freeze the exposure short enough to stop motion, let ISO rise
 * to compensate, and keep autofocus running because the subject is moving.
 */
object SportExposureOptionsFactory {

    /**
     * Whether the recipe can be applied at all. Deliberately the same predicate the profile is
     * offered on, so a profile the menu never showed can never reach the capture request.
     *
     * The session applies the numbers into its own single `CaptureRequestOptions.Builder` rather
     * than receiving a built bag from here - it already merges manual exposure, macro focus and
     * white balance into one, and a second bag would clobber them.
     */
    fun isApplicable(capabilities: CameraRuntimeCapabilities): Boolean =
        PhotoProfile.SPORT.isAvailable(capabilities) &&
            resolvedExposureNs(capabilities) != null &&
            resolvedIso(capabilities) != null

    /** The exposure the recipe resolves to, exposed so the session and its test agree on one number. */
    fun resolvedExposureNs(capabilities: CameraRuntimeCapabilities): Long? =
        capabilities.shutterRangeNs?.let {
            PhotoProfile.SPORT_TARGET_EXPOSURE_NS.coerceIn(it.lower, it.upper)
        }

    /**
     * Capped rather than taken from the sensor maximum: the top of a phone's ISO range is usually
     * noise the user would not have chosen, and a sport frame is worth more sharp than bright.
     */
    fun resolvedIso(capabilities: CameraRuntimeCapabilities): Int? =
        capabilities.isoRange?.let { ISO_CAP.coerceAtMost(it.upper).coerceAtLeast(it.lower) }

    /** Highest ISO the sport recipe will ask for before it prefers a darker frame to a noisier one. */
    const val ISO_CAP = 1600
}
