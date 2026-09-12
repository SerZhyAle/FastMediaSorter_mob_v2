package com.sza.fastmediasorter.domain.delivery

/**
 * S2652: the live size of a delivery asset that is republished outside the build.
 *
 * The stream catalog is not a [DeliverableSet] and carries no descriptor, so nothing in the build
 * knows how large it is - the only number the app had was a constant copied off one publish in July,
 * which understated the September archive threefold. This reads the size off the very asset the
 * import will fetch, so the two cannot disagree.
 *
 * Absence is not an error. No network, an HTTP failure, a response with no length: every one of them
 * yields null, which the caller reads as "use the compiled fallback" - the same contract
 * [ArtworkManifestSource] states for the artwork payloads.
 */
interface DeliveryAssetSizeSource {

    /** Published size of `stream-catalog.zip` in bytes, or null when it cannot be measured. */
    suspend fun streamCatalogBytes(): Long?
}

/**
 * S2652: the one home of a delivery asset URL that both measures and downloads.
 *
 * The size shown before the download and the download itself must name the same asset, or the
 * promise on screen belongs to a different file than the one that arrives.
 */
object DeliveryAssets {

    const val STREAM_CATALOG_URL =
        "https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/stream-catalog.zip"
}
