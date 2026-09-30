package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.domain.model.PinnedStreamChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S4016: the pinned set in pin order, mapped off the Room entity so a screen outside the streams
 * window reads a domain model rather than the catalog table.
 */
class ObservePinnedStreamChannelsUseCase @Inject constructor(
    private val observePinnedSources: ObservePinnedStreamSourcesUseCase
) {
    operator fun invoke(): Flow<List<PinnedStreamChannel>> =
        observePinnedSources()
            .map { sources -> sources.map { PinnedStreamChannel(id = it.id, title = it.title) } }
            .distinctUntilChanged()
}
