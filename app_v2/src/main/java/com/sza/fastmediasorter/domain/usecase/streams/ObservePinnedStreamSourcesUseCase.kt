package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * S0756: observes the pinned stream channels in pin order for the main-window streams panel.
 * [distinctUntilChanged] guards against redundant re-emissions so the panel adapter only diffs on a
 * real change to the pinned set.
 */
class ObservePinnedStreamSourcesUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {
    operator fun invoke(): Flow<List<StreamSourceEntity>> =
        repository.observePinnedSources().distinctUntilChanged()
}
