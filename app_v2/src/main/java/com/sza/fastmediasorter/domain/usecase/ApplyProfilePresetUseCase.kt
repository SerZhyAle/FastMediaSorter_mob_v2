package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.xr.VrProfileSettingsSync
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.data.preset.DeviceProfilePresetApplier
import com.sza.fastmediasorter.data.preset.DeviceProfilePresetCsvDataSource
import com.sza.fastmediasorter.domain.repository.DeviceProfileRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Applies the owner-authored device-profile preset to the current settings.
 *
 * The matrix lives in `assets/device_profile_presets.csv` (one row per [com.sza.fastmediasorter.domain.model.AppSettings]
 * field, one column per profile). Only non-empty cells are overrides; an empty cell keeps the
 * current value (on a fresh install that equals the code default — the intended "empty = default"
 * semantic). VR-capable flavors may still align the global 3D/VR toggle with the selected profile
 * even when the CSV column itself is empty.
 */
class ApplyProfilePresetUseCase @Inject constructor(
    private val presetDataSource: DeviceProfilePresetCsvDataSource,
    private val presetApplier: DeviceProfilePresetApplier,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: DeviceProfileRepository,
    private val vrProfileSettingsSync: VrProfileSettingsSync,
) {
    suspend fun apply(profileType: DeviceProfileType, presetVersion: Int = 1): Result<Unit> {
        Timber.i("ApplyProfilePresetUseCase: Applying preset for $profileType (v$presetVersion)")

        val overrides = presetDataSource.load()[profileType].orEmpty()

        return runCatching {
            val current = settingsRepository.getSettings().first()
            val presetUpdated = overrides.entries.fold(current) { acc, (field, raw) ->
                presetApplier.applyOverride(acc, field, raw)
            }
            val updated = vrProfileSettingsSync.align(profileType, presetUpdated)
            if (overrides.isNotEmpty() || updated != current) {
                settingsRepository.updateSettings(updated)
            } else {
                Timber.i("ApplyProfilePresetUseCase: No settings changes required for $profileType")
            }
            if (overrides.isNotEmpty()) {
                profileRepository.updatePresetApplied(presetVersion).getOrThrow()
                Timber.i("ApplyProfilePresetUseCase: Preset applied successfully for $profileType (${overrides.size} overrides)")
            }
        }
    }
}
