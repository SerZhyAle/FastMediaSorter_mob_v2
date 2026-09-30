package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.WatchFaceOpenResult

/**
 * S4009: opens the watch face's store listing on a paired watch.
 *
 * Bound to the Wear bridge in the Wear-capable flavors and to an inert implementation everywhere
 * else, so `src/main` never asks which flavor it runs in.
 */
interface WatchFaceInstallRepository {

    /** Opens the listing on the first connected watch and reports how that went. */
    suspend fun openListingOnWatch(): WatchFaceOpenResult
}
