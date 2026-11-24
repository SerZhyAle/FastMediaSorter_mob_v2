package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S3365: the scheduled-operations program's two switches - on/off, the registry disable target, and the
 * pause that keeps the operations stored but stops their runs.
 */
class ManageScheduledOperationsSwitchUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    fun observeEnabled(): Flow<Boolean> = settingsRepository.getSettings().map { it.enableScheduledOperations }

    fun observePaused(): Flow<Boolean> = settingsRepository.getSettings().map { it.scheduledOperationsPaused }

    suspend fun setEnabled(enabled: Boolean) {
        settingsRepository.updateSettings { it.copy(enableScheduledOperations = enabled) }
    }

    suspend fun setPaused(paused: Boolean) {
        settingsRepository.updateSettings { it.copy(scheduledOperationsPaused = paused) }
    }
}
