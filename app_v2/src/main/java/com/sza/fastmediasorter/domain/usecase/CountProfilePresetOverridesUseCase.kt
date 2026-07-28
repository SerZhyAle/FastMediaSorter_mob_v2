package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.data.preset.DeviceProfilePresetCsvDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * S1216: how many settings the chosen device profile will overwrite.
 *
 * Counts the same non-empty override map that [ApplyProfilePresetUseCase] folds onto the current
 * settings, so the number shown in the confirmation is by construction the number that gets
 * written - it cannot drift away from reality as the matrix grows.
 */
class CountProfilePresetOverridesUseCase @Inject constructor(
    private val dataSource: DeviceProfilePresetCsvDataSource
) {

    /**
     * Returns 0 for a profile the matrix does not mention - nothing would be overwritten, so the
     * caller can skip the confirmation entirely.
     *
     * Off the main thread because the data source parses the CSV asset on first access.
     */
    suspend operator fun invoke(profileType: DeviceProfileType): Int = withContext(Dispatchers.IO) {
        dataSource.load()[profileType]
            ?.count { it.value.isNotBlank() }
            ?: 0
    }
}
