package com.sza.fastmediasorter.vr.ui

/**
 * Pure math class: converts controller aim-ray NDC coordinates (Phase 02 output)
 * into UV coordinates on the interactive panel texture (Phase 03).
 *
 * NDC convention: origin = panel centre, +X right, +Y up, range [-1, 1] when on-panel.
 * UV convention:  origin = panel top-left, +U right, +V down, range [0, 1].
 *
 * Mapping: U = (ndcX + 1f) / 2f,  V = (1f − ndcY) / 2f.
 *
 * No Android dependencies — testable in isolation.
 */
class VrRayPanelHitTester {

    data class HitResult(val u: Float, val v: Float) {
        /** True when the ray missed the panel plane (u or v outside [0, 1]). */
        val isMiss: Boolean get() = u < 0f
    }

    /**
     * Compute the UV hit for a controller aim-ray NDC sample.
     *
     * Returns [UV_MISS] when the ray is outside the ±[MISS_THRESHOLD] guard band,
     * meaning it cleanly misses the panel quad.
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
        /** Guard-band threshold: NDC values outside ±1.5 are treated as a miss. */
        private const val MISS_THRESHOLD = 1.5f

        /** Sentinel returned for a ray miss; `isMiss` is true. */
        val UV_MISS = HitResult(-1f, -1f)
    }
}
