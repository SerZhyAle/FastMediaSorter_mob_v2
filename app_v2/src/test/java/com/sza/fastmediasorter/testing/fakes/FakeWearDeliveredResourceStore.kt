package com.sza.fastmediasorter.testing.fakes

import com.sza.fastmediasorter.data.repository.wear.WearDeliveredResourceStore

/**
 * S2909: in-memory delivered-id store.
 *
 * Starts at null, which is what a phone that has never completed a push reports, so a test that wants
 * the narrowed withdrawal set has to state what the watch was given first - exactly as the production
 * store forces the push leg to.
 */
class FakeWearDeliveredResourceStore : WearDeliveredResourceStore {

    var delivered: Set<String>? = null

    override suspend fun read(): Set<String>? = delivered

    override suspend fun write(ids: Set<String>) {
        delivered = ids
    }
}
