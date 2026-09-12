package com.sza.fastmediasorter.domain.usecase.stopwatch

import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Reads the stopwatch's own four remembered values out of the shared settings (S1411 ADR-7).
 *
 * Exists so the screen reaches the settings through a use case instead of injecting the repository into
 * its ViewModel (S2103). [distinctUntilChanged] keeps an unrelated settings write - any of the other
 * couple of hundred fields - from re-emitting here and resetting the participant count or re-selecting
 * the accompaniment track while a measurement is running.
 */
class ObserveStopwatchSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    operator fun invoke(): Flow<StopwatchSettings> = settingsRepository.getSettings()
        .map {
            StopwatchSettings(
                participantCount = it.stopwatchParticipantCount,
                musicEnabled = it.stopwatchMusicEnabled,
                musicUri = it.stopwatchMusicUri,
                volumeKeysDriveMeasurement = it.stopwatchVolumeKeysControl,
            )
        }
        .distinctUntilChanged()
}
