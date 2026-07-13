package com.sza.fastmediasorter.domain.usecase.link

/**
 * S0973: paces multi-item link downloads (carousels/albums) so the outbound traffic looks
 * human rather than a burst of back-to-back requests. Bound only in the noLegal flavor (via an
 * `@IntoSet` multibinding); in every other flavor the injected set is empty and no pause is added,
 * keeping the flavor-specific behaviour out of `src/main` without a `BuildConfig` guard (Rule 14).
 */
interface LinkDownloadPacer {
    /** Suspend for a humanized, interruptible interval between two carousel items. */
    suspend fun pauseBetweenItems()
}
