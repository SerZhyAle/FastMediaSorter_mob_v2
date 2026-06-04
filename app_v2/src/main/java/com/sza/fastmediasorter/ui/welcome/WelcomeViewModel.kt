package com.sza.fastmediasorter.ui.welcome

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.DeviceProfileRepository
import com.sza.fastmediasorter.domain.detector.DeviceProfileDetector
import com.sza.fastmediasorter.domain.usecase.ApplyProfilePresetUseCase
import com.sza.fastmediasorter.ui.profile.DeviceProfileAvailability
import com.sza.fastmediasorter.data.model.DeviceProfile
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.data.model.DeviceProfileSource
import com.sza.fastmediasorter.data.model.DetectionConfidence
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val deviceProfileRepository: DeviceProfileRepository,
    private val deviceProfileDetector: DeviceProfileDetector,
    private val applyProfilePresetUseCase: ApplyProfilePresetUseCase,
    private val deviceProfileAvailability: DeviceProfileAvailability
) : BaseViewModel<WelcomeState, WelcomeEvent>() {

    companion object {
        private const val PREFS_NAME = "welcome_prefs"
        private const val KEY_WELCOME_COMPLETED = "welcome_completed"
        private const val KEY_FIRST_RUN_AFTER_WELCOME = "first_run_after_welcome"
        private const val KEY_DEFAULT_PLAYER_ONBOARDING_SHOWN = "onboarding_default_player_shown"
        private const val APP_PREFS_NAME = "app_prefs"
        private const val KEY_MEDIA_PERMISSIONS_GRANTED = "media_permissions_granted"
    }

    override fun getInitialState(): WelcomeState = WelcomeState()

    init {
        detectDeviceProfile()
    }

    private fun detectDeviceProfile() {
        viewModelScope.launch(exceptionHandler) {
            val result = deviceProfileDetector.detectProfile()
            // The VR profile may be hidden in this flavor; never recommend an unavailable profile.
            val recommended = if (deviceProfileAvailability.isAvailable(result.profile)) {
                result.profile
            } else {
                DeviceProfileType.PERSONAL_SMARTPHONE
            }
            val confidence = if (recommended == result.profile) result.confidence else DetectionConfidence.LOW
            updateState {
                it.copy(
                    recommendedProfile = recommended,
                    selectedProfile = recommended,
                    detectorConfidence = confidence
                )
            }
            Timber.i("Device profile auto-detected: detected=${result.profile}, recommended=$recommended, confidence=$confidence")
        }
    }

    fun onProfileSelected(type: DeviceProfileType) {
        updateState { it.copy(selectedProfile = type) }
        Timber.i("Device profile manually selected in Welcome: $type")
    }

    fun saveDeviceProfile(isSkipped: Boolean) {
        viewModelScope.launch(exceptionHandler) {
            val currentState = state.value
            val finalType = if (isSkipped) {
                currentState.recommendedProfile ?: DeviceProfileType.PERSONAL_SMARTPHONE
            } else {
                currentState.selectedProfile ?: DeviceProfileType.PERSONAL_SMARTPHONE
            }

            val finalSource = if (isSkipped) {
                DeviceProfileSource.AUTO_SKIPPED
            } else {
                DeviceProfileSource.MANUAL_SELECTION
            }

            val finalConfidence = if (isSkipped) {
                currentState.detectorConfidence
            } else {
                DetectionConfidence.NONE
            }

            val profile = DeviceProfile(
                type = finalType,
                source = finalSource,
                confidence = finalConfidence,
                presetVersion = 0,
                appliedAtInstallTime = finalType != DeviceProfileType.OTHER,
                lastModified = System.currentTimeMillis()
            )

            deviceProfileRepository.saveProfile(profile)
            Timber.i("Device profile saved on welcome flow completion: $profile (isSkipped=$isSkipped)")

            // Apply preset values for this profile after the selected profile is persisted.
            applyProfilePresetUseCase.apply(finalType, presetVersion = 1)
        }
    }

    fun setWelcomeCompleted() {
        StrictModeHelper.allowDiskWrites {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_WELCOME_COMPLETED, true)
                .apply()
        }
    }

    fun isWelcomeCompleted(): Boolean {
        return StrictModeHelper.allowDiskReads {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_WELCOME_COMPLETED, false)
        }
    }

    /**
     * Check if this is the first app run after completing welcome screen.
     * Returns true only once - the first time after welcome completion.
     */
    fun isFirstRunAfterWelcome(): Boolean {
        return StrictModeHelper.allowDiskReads {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_FIRST_RUN_AFTER_WELCOME, true) // Default true = first run
        }
    }

    /**
     * Mark that the first run after welcome has been completed.
     * This ensures Settings opens only once after initial welcome.
     */
    fun setFirstRunCompleted() {
        StrictModeHelper.allowDiskWrites {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_FIRST_RUN_AFTER_WELCOME, false)
                .apply()
        }
    }

    fun isDefaultPlayerOnboardingShown(): Boolean {
        return StrictModeHelper.allowDiskReads {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_DEFAULT_PLAYER_ONBOARDING_SHOWN, false)
        }
    }

    fun markDefaultPlayerOnboardingShown() {
        StrictModeHelper.allowDiskWrites {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DEFAULT_PLAYER_ONBOARDING_SHOWN, true)
                .apply()
        }
    }

    fun enablePrimaryMediaPlayer() {
        viewModelScope.launch {
            val current = settingsRepository.getSettings().first()
            if (!current.isPrimaryMediaPlayer) {
                settingsRepository.updateSettings(current.copy(isPrimaryMediaPlayer = true))
            }
        }
    }

    fun setMediaPermissionsGranted(granted: Boolean) {
        StrictModeHelper.allowDiskWrites {
            context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MEDIA_PERMISSIONS_GRANTED, granted)
                .apply()
        }
    }
}

data class WelcomeState(
    val recommendedProfile: DeviceProfileType? = null,
    val selectedProfile: DeviceProfileType? = null,
    val detectorConfidence: DetectionConfidence = DetectionConfidence.NONE
)

sealed class WelcomeEvent
