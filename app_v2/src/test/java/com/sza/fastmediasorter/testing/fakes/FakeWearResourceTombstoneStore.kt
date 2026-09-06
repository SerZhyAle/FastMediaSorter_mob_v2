package com.sza.fastmediasorter.testing.fakes

import com.sza.fastmediasorter.data.repository.wear.WearResourceTombstoneStore
import com.sza.fastmediasorter.domain.model.WearSourceTombstonePayload

/**
 * S2507: in-memory tombstone store. Shared rather than file-private because the delete leg, the push
 * leg and the import leg each need it, and three copies would let them disagree about the semantics
 * the merge rule is judged against.
 */
class FakeWearResourceTombstoneStore : WearResourceTombstoneStore {

    val tombstones = mutableListOf<WearSourceTombstonePayload>()

    override suspend fun read(): List<WearSourceTombstonePayload> = tombstones.toList()

    override suspend fun record(tombstone: WearSourceTombstonePayload) {
        tombstones.removeAll { it.id == tombstone.id }
        tombstones.add(tombstone)
    }

    override suspend fun forget(resourceId: String) {
        tombstones.removeAll { it.id == resourceId }
    }
}
