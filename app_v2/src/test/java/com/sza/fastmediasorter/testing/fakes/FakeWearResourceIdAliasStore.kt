package com.sza.fastmediasorter.testing.fakes

import com.sza.fastmediasorter.data.repository.wear.WearResourceIdAliasStore

/**
 * S2507 phase 04: in-memory alias store. Shared with [FakeWearResourceTombstoneStore] for the same
 * reason - the import leg both writes an alias and reads it back a batch later, and a file-private
 * copy per test class would let the two halves disagree.
 */
class FakeWearResourceIdAliasStore : WearResourceIdAliasStore {

    val aliases = mutableMapOf<String, Long>()

    override suspend fun resolve(foreignId: String): Long? = aliases[foreignId]

    override suspend fun record(foreignId: String, resourceId: Long) {
        aliases[foreignId] = resourceId
    }

    override suspend fun forget(foreignId: String) {
        aliases.remove(foreignId)
    }
}
