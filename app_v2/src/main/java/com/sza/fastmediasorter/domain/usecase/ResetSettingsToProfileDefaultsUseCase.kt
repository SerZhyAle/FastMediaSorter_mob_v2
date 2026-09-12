package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.repository.DeviceProfileRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Resets settings to the state the device's own profile describes: factory defaults first, then the
 * stored profile's preset overrides folded back on top (S2664).
 *
 * Before this, a reset ended at the defaults of a device with no profile at all - the profile record
 * survived the reset untouched, so the device kept claiming to be a car head unit while holding a
 * smartphone's settings.
 *
 * Applies silently: the profile was recorded when the user chose it, so a reset has nothing to ask.
 * [DeviceProfileType.OTHER] carries no overrides by contract and stops after the factory half. An
 * install that never stored a profile is indistinguishable here from one that stored
 * [DeviceProfileType.PERSONAL_SMARTPHONE] - [DeviceProfileRepository.getCurrentProfile] substitutes
 * that type rather than emitting nothing - and that substitution is what the rest of the app already
 * treats as the current profile.
 */
class ResetSettingsToProfileDefaultsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val profileRepository: DeviceProfileRepository,
    private val applyProfilePresetUseCase: ApplyProfilePresetUseCase,
) {
    suspend operator fun invoke() {
        settingsRepository.resetToDefaults()
        val profileType = profileRepository.getCurrentProfile().first().type
        if (profileType == DeviceProfileType.OTHER) return
        // applySettingsOnly, not apply: the bookkeeping half records that a preset was applied AT
        // INSTALL TIME, which a settings reset is not.
        applyProfilePresetUseCase.applySettingsOnly(profileType).onFailure { error ->
            Timber.e(error, "ResetSettingsToProfileDefaultsUseCase: preset re-apply failed for %s", profileType)
        }
    }
}
