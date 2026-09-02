package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import javax.inject.Inject

/**
 * S2031: resolve the channel a launcher cell is bound to from its stored identity key.
 *
 * Answers null for a key the catalog no longer carries: a placed cell outlives the row it was created
 * from, and the caller decides what an absent channel looks like on the desktop.
 */
class GetStreamSourceByIdentityUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {
    suspend operator fun invoke(identityKey: String): StreamSourceEntity? =
        repository.getByIdentityOrId(identityKey)
}
