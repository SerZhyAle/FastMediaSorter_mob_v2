package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.WatchFaceOpenResult
import com.sza.fastmediasorter.domain.model.WatchInstallOffer

/**
 * S4009: opens the watch face's store listing on a paired watch; S4012 adds the watch app's listing
 * and the lookup behind the one-time install offer.
 *
 * Bound to the Wear bridge in the Wear-capable flavors and to an inert implementation everywhere
 * else, so `src/main` never asks which flavor it runs in.
 */
interface WatchFaceInstallRepository {

    /** Opens the listing on the first connected watch and reports how that went. */
    suspend fun openListingOnWatch(): WatchFaceOpenResult

    /** S4012: opens the watch app's listing on the first connected watch. */
    suspend fun openWatchAppListingOnWatch(): WatchFaceOpenResult

    /**
     * S4012: the first connected watch and whether it already carries the watch app, or null when no
     * watch answers. Never throws and never waits long: it runs on the startup path.
     */
    suspend fun findInstallOffer(): WatchInstallOffer?
}
