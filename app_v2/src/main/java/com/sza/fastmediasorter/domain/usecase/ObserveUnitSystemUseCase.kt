package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.UnitSystem
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S2731: reads the phone's measurement system out of the shared settings, for the wear companion
 * window's `payload()` builder - the same field `WeatherRepositoryImpl` already reads, exposed to a
 * screen through a use case instead of injecting the repository into its ViewModel (S2103).
 * [distinctUntilChanged] keeps an unrelated settings write from re-emitting here.
 */
class ObserveUnitSystemUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    operator fun invoke(): Flow<UnitSystem> = settingsRepository.getSettings()
        .map { it.unitSystem }
        .distinctUntilChanged()
}
