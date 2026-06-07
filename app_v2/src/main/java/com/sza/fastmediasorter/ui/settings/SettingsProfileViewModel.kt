package com.sza.fastmediasorter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.data.model.DetectionConfidence
import com.sza.fastmediasorter.data.model.DeviceProfile
import com.sza.fastmediasorter.data.model.DeviceProfileSource
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.repository.DeviceProfileRepository
import com.sza.fastmediasorter.domain.usecase.ApplyProfilePresetUseCase
import com.sza.fastmediasorter.domain.usecase.EnsureAllFilesPredefinedResourceUseCase
import com.sza.fastmediasorter.domain.usecase.ProfileImpliesAllFilesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsProfileViewModel @Inject constructor(
    private val deviceProfileRepository: DeviceProfileRepository,
    private val applyProfilePresetUseCase: ApplyProfilePresetUseCase,
    private val profileImpliesAllFilesUseCase: ProfileImpliesAllFilesUseCase,
    private val ensureAllFilesPredefinedResourceUseCase: EnsureAllFilesPredefinedResourceUseCase
) : ViewModel() {

    val currentProfile: StateFlow<DeviceProfile?> = deviceProfileRepository.getCurrentProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun saveProfile(type: DeviceProfileType, ensureAllFilesResource: Boolean = false) {
        viewModelScope.launch {
            val profile = DeviceProfile(
                type = type,
                source = DeviceProfileSource.MANUAL_SELECTION,
                confidence = DetectionConfidence.NONE,
                presetVersion = 0,
                appliedAtInstallTime = type != DeviceProfileType.OTHER,
                lastModified = System.currentTimeMillis()
            )
            deviceProfileRepository.saveProfile(profile)
            Timber.i("Device profile updated in Settings: $profile")

            // Apply preset values only after the explicit Settings confirmation path saves the profile.
            applyProfilePresetUseCase.apply(type, presetVersion = 1)
                .onSuccess {
                    if (ensureAllFilesResource) {
                        ensureAllFilesPredefinedResourceUseCase()
                            .onFailure { Timber.e(it, "SettingsProfileViewModel: failed to ensure All Files resource") }
                    }
                }
                .onFailure { Timber.e(it, "SettingsProfileViewModel: failed to apply profile preset") }
        }
    }

    suspend fun shouldConfirmAllFilesProvision(type: DeviceProfileType): Boolean {
        return profileImpliesAllFilesUseCase(type) && !ensureAllFilesPredefinedResourceUseCase.exists()
    }
}
