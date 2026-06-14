package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.data.preset.DeviceProfilePresetCsvDataSource
import javax.inject.Inject

class ProfileImpliesAllFilesUseCase @Inject constructor(
    private val presetDataSource: DeviceProfilePresetCsvDataSource
) {
    operator fun invoke(profileType: DeviceProfileType): Boolean {
        val raw = presetDataSource.load()[profileType]?.get(FIELD_ALL_FILES) ?: return false
        return raw.equals(TRUE_VALUE, ignoreCase = true)
    }

    private companion object {
        private const val FIELD_ALL_FILES = "allFiles"
        private const val TRUE_VALUE = "TRUE"
    }
}
