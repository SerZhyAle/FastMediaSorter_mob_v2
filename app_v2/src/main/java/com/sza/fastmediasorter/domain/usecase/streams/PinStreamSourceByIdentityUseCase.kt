package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import javax.inject.Inject

/**
 * S4016: pins a channel by its folded identity (S1832) rather than by catalog row id, because the
 * stream picker hands back an identity - the same key the watch's pin deltas use (S2497).
 */
class PinStreamSourceByIdentityUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {
    suspend operator fun invoke(identityKey: String) = repository.pinByIdentity(identityKey)
}
