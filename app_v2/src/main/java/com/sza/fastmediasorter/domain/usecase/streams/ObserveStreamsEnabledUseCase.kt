package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S4016: the Streams master switch (S0575) as a stream, for a surface that exists only while streams
 * are on - the Wear companion's pinned-streams group is drawn or withheld by it.
 */
class ObserveStreamsEnabledUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<Boolean> =
        settingsRepository.getSettings().map { it.enableStreams }.distinctUntilChanged()
}
