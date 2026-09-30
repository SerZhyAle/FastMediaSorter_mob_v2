package com.sza.fastmediasorter.data.wear

import com.sza.fastmediasorter.domain.model.WatchFaceOpenResult
import com.sza.fastmediasorter.domain.model.WatchInstallOffer
import com.sza.fastmediasorter.domain.repository.WatchFaceInstallRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4009: inert watch face installer for the `wearStub` source set.
 *
 * These builds have no bridge to a watch at all, so [WatchFaceOpenResult.NoWatch] is the truth here
 * rather than a placeholder; their UI never offers the action in the first place.
 */
@Singleton
class NoOpWatchFaceInstallRepository @Inject constructor() : WatchFaceInstallRepository {

    override suspend fun openListingOnWatch(): WatchFaceOpenResult = WatchFaceOpenResult.NoWatch

    override suspend fun openWatchAppListingOnWatch(): WatchFaceOpenResult = WatchFaceOpenResult.NoWatch

    override suspend fun findInstallOffer(): WatchInstallOffer? = null
}
