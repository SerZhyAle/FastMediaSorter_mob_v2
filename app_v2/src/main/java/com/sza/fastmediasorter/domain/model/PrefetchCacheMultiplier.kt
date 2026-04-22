package com.sza.fastmediasorter.domain.model

/**
 * User override for the computed pre-cache duration.
 *
 * Applied in [PrefetchFormula.compute] after the physics-based target is derived.
 * LESS reduces by a factor, AUTO is the identity, MORE adds a fixed headroom
 * (not a multiplier, to stay predictable for the user).
 */
enum class PrefetchCacheMultiplier {
    // Side-effect: LESS also hides the prefetch overlay pill and suppresses the offload offer dialog,
    // since a user who wants minimal buffering implicitly wants minimal UI noise too.
    LESS,
    AUTO,
    MORE;

    companion object {
        val DEFAULT: PrefetchCacheMultiplier = AUTO

        fun fromName(name: String?): PrefetchCacheMultiplier =
            values().firstOrNull { it.name == name } ?: DEFAULT
    }
}
