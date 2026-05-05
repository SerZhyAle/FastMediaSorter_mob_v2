package com.sza.fastmediasorter.vr.ui

/**
 * Pure math: converts controller aim-ray NDC coordinates emitted by
 * `OpenXrInput::syncControllerAimRay` into UV coordinates on the HUD plane
 * texture for `VrHudElementRegistry.elementAt(u, v)` lookup.
 *
 * The HUD plane is head-locked, ~1.0 m × 0.3 m at 1.5 m forward, ~20° below
 * eye-line — geometry inherited from S0009 §6 #4 and pinned in C++ as
 * `kHudHalfW=0.5`, `kHudHalfH=0.15`, `kHudCentreY=-0.35`.
 *
 * Aim-pose source (S0024 strategic §6.1, Resolved 2026-04-28): the same
 * controller-aim XrSpace already drives the visual ray in `VrControllerRayManager`.
 * No second pose poll — the NDC emitted on `XrInputCallback.onControllerPointerMove`
 * is already the HUD-plane intersection.
 *
 * NDC convention: origin = HUD-plane centre, +X right, +Y up, range [-1, 1] when on-plane.
 * UV convention:  origin = HUD-plane top-left, +U right, +V down, range [0, 1].
 *
 * Mapping mirrors [VrRayPanelHitTester] so consumers can swap the two interchangeably.
 *
 * No Android dependencies — testable in isolation.
 */
class VrHudHitTester {

    data class HitResult(val u: Float, val v: Float) {
        /** True when the ray missed the HUD plane (u or v outside [0, 1]). */
        val isMiss: Boolean get() = u < 0f
    }

    /**
     * Compute the UV hit for a controller aim-ray NDC sample.
     *
     * Returns [UV_MISS] when the ray is outside the ±[MISS_THRESHOLD] guard band,
     * meaning it cleanly misses the HUD quad.
     */
    fun computeHit(ndcX: Float, ndcY: Float): HitResult {
        if (ndcX < -MISS_THRESHOLD || ndcX > MISS_THRESHOLD ||
            ndcY < -MISS_THRESHOLD || ndcY > MISS_THRESHOLD
        ) {
            return UV_MISS
        }
        val u = (ndcX + 1f) * 0.5f
        val v = (1f - ndcY) * 0.5f
        return if (u < 0f || u > 1f || v < 0f || v > 1f) UV_MISS else HitResult(u, v)
    }

    companion object {
        /** Guard-band threshold mirroring [VrRayPanelHitTester]: ±1.5 NDC = miss. */
        private const val MISS_THRESHOLD = 1.5f

        /** Sentinel returned for a ray miss; `isMiss` is true. */
        val UV_MISS = HitResult(-1f, -1f)
    }
}
