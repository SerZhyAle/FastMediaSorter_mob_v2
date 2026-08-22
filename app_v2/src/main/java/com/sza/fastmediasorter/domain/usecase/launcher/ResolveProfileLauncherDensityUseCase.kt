package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.data.preset.DeviceProfilePresetCsvDataSource
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.DeviceProfileRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.abs

/**
 * S1886: resolves the icon density the launcher reset dialog preselects, taken from the device-profile
 * preset matrix rather than the factory 1.0.
 *
 * An empty `launcherDensityFactor` cell in `assets/device_profile_presets.csv` means "no override", not
 * zero - five of the eleven profile columns carry no cell at all - so an absent or unparseable cell falls
 * back to the [AppSettings] default.
 *
 * The result is snapped to the nearest [AppSettings.LAUNCHER_DENSITY_OPTIONS] entry, because the reset
 * dialog renders a four-step selector and cannot display a value outside that list.
 *
 * [com.sza.fastmediasorter.domain.usecase.ApplyProfilePresetUseCase] is the precedent for a domain use
 * case depending on the preset data source.
 */
class ResolveProfileLauncherDensityUseCase @Inject constructor(
    private val presetDataSource: DeviceProfilePresetCsvDataSource,
    private val profileRepository: DeviceProfileRepository,
) {

    suspend operator fun invoke(): Float {
        val type = profileRepository.getCurrentProfile().first().type
        val override = presetDataSource.load()[type]?.get(PRESET_FIELD)?.toFloatOrNull()
        return snapToOption(override ?: AppSettings().launcherDensityFactor)
    }

    private fun snapToOption(value: Float): Float =
        AppSettings.LAUNCHER_DENSITY_OPTIONS.minBy { option -> abs(option - value) }

    companion object {
        private const val PRESET_FIELD = "launcherDensityFactor"
    }
}
