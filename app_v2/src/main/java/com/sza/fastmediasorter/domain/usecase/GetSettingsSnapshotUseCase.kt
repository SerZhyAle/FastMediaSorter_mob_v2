package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** The settings as they are right now, for a caller that reads them once instead of observing them. */
class GetSettingsSnapshotUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): AppSettings = settingsRepository.getSettings().first()
}
