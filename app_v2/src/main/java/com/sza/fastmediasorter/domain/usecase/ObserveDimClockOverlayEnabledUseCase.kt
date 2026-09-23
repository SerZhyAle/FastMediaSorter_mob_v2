package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S3330: reads the phone's dim-clock overlay toggle out of the shared settings, for the wear
 * companion window's `payload()` builder - the same shape as [ObserveUnitSystemUseCase] beside it.
 * [distinctUntilChanged] keeps an unrelated settings write from re-emitting here.
 */
class ObserveDimClockOverlayEnabledUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    operator fun invoke(): Flow<Boolean> = settingsRepository.getSettings()
        .map { it.dimClockOverlayEnabled }
        .distinctUntilChanged()
}
