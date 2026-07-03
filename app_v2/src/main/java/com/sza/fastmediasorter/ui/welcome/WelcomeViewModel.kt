package com.sza.fastmediasorter.ui.welcome

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.DeviceProfileRepository
import com.sza.fastmediasorter.domain.detector.DeviceProfileDetector
import com.sza.fastmediasorter.domain.usecase.ApplyProfilePresetUseCase
import com.sza.fastmediasorter.domain.usecase.EnsureAllFilesPredefinedResourceUseCase
import com.sza.fastmediasorter.domain.usecase.ProfileImpliesAllFilesUseCase
import com.sza.fastmediasorter.domain.usecase.SeedDefaultGestureBindingsUseCase
import com.sza.fastmediasorter.ui.profile.DeviceProfileAvailability
import com.sza.fastmediasorter.data.model.DeviceProfile
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.data.model.DeviceProfileSource
import com.sza.fastmediasorter.data.model.DetectionConfidence
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
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
    private val deviceProfileAvailability: DeviceProfileAvailability,
    private val profileImpliesAllFilesUseCase: ProfileImpliesAllFilesUseCase,
    private val ensureAllFilesPredefinedResourceUseCase: EnsureAllFilesPredefinedResourceUseCase,
    private val seedDefaultGestureBindingsUseCase: SeedDefaultGestureBindingsUseCase,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) : BaseViewModel<WelcomeState, WelcomeEvent>() {

    companion object {
        private const val PREFS_NAME = "welcome_prefs"
        private const val KEY_WELCOME_COMPLETED = "welcome_completed"
        private const val KEY_FIRST_RUN_AFTER_WELCOME = "first_run_after_welcome"
        private const val KEY_DEFAULT_PLAYER_ONBOARDING_SHOWN = "onboarding_default_player_shown"
        private const val KEY_GESTURE_DEFAULTS_SEEDED = "gesture_defaults_seeded"
        private const val APP_PREFS_NAME = "app_prefs"
        private const val KEY_MEDIA_PERMISSIONS_GRANTED = "media_permissions_granted"
    }

    // First-run only: the device profile whose settings preset was already applied early (before the
    // user reached the capability/permission pages), and whether it carried any overrides. Kept so
    // Finish does NOT re-apply the settings (that would clobber the user's later toggle deviations) yet
    // still records the bookkeeping. @Volatile because the early apply and Finish both run on the
    // application scope (IO) and may observe these from different threads.
    @Volatile
    private var firstRunPresetAppliedFor: DeviceProfileType? = null
    @Volatile
    private var firstRunPresetHadOverrides: Boolean = false

    override fun getInitialState(): WelcomeState = WelcomeState()

    init {
        detectDeviceProfile()
        maybeSeedDefaultGestureBindings()
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
            // On a Settings re-entry pre-select the profile the user previously stored (strategic §6:
            // pre-populate pages from settings), falling back to the recommendation if that profile is
            // not available in this flavor. First run keeps the detected recommendation pre-selected.
            val selected = if (isWelcomeCompleted()) {
                val stored = deviceProfileRepository.getCurrentProfile().first().type
                if (deviceProfileAvailability.isAvailable(stored)) stored else recommended
            } else {
                recommended
            }
            updateState {
                it.copy(
                    recommendedProfile = recommended,
                    selectedProfile = selected,
                    detectorConfidence = confidence
                )
            }
            Timber.i("Device profile auto-detected: detected=${result.profile}, recommended=$recommended, selected=$selected, confidence=$confidence")
        }
    }

    fun onProfileSelected(type: DeviceProfileType) {
        updateState { it.copy(selectedProfile = type) }
        Timber.i("Device profile manually selected in Welcome: $type")
    }

    /**
     * First-run only: eagerly apply the selected profile's settings preset so the capability/permission
     * pages that follow the device-profile page render the profile's defaults. Any change the user then
     * makes there is a deliberate deviation that must survive - which is why [saveDeviceProfile] no
     * longer re-applies the settings at Finish. Deduped per profile so navigating back and forth (or a
     * later re-selection) only re-applies when the chosen profile actually changes.
     *
     * Re-entry from Settings is intentionally excluded: it keeps the Finish-time skip/confirm semantics
     * so tuned settings are protected.
     */
    fun applyFirstRunPresetForSelectedProfile() {
        if (isWelcomeCompleted()) return
        val type = state.value.selectedProfile
            ?: state.value.recommendedProfile
            ?: DeviceProfileType.PERSONAL_SMARTPHONE
        if (firstRunPresetAppliedFor == type) return
        applicationScope.launch(exceptionHandler) {
            applyProfilePresetSettings(type)
        }
    }

    /** Apply [type]'s settings preset (no profile bookkeeping) and, when the profile implies the All
     *  Files resource, ensure it exists. Records the dedup markers consumed by [saveDeviceProfile]. */
    private suspend fun applyProfilePresetSettings(type: DeviceProfileType): Boolean {
        val hadOverrides = applyProfilePresetUseCase.applySettingsOnly(type)
            .onSuccess {
                if (profileImpliesAllFilesUseCase(type)) {
                    ensureAllFilesPredefinedResourceUseCase()
                        .onFailure { Timber.e(it, "WelcomeViewModel: failed to ensure All Files resource") }
                }
            }
            .onFailure { Timber.e(it, "WelcomeViewModel: failed to apply device-profile preset settings") }
            .getOrDefault(false)
        firstRunPresetAppliedFor = type
        firstRunPresetHadOverrides = hadOverrides
        return hadOverrides
    }

    /** Profiles selectable in this flavor (VR hidden where unavailable). The dedicated device-profile
     *  page (S0399) renders these as a grid; ordering (small-screen-first) is applied by the page. */
    fun selectableProfiles(): List<DeviceProfileType> = deviceProfileAvailability.selectableProfiles

    fun saveDeviceProfile(isSkipped: Boolean) {
        // Capture the screen state and the re-entry flag synchronously, before launching: the Finish
        // handler calls setWelcomeCompleted() immediately after this, and applicationScope dispatches
        // on IO (not Main.immediate), so reading these inside the coroutine would race and misread a
        // genuine first run as a re-entry - silently skipping the first-run preset.
        val currentState = state.value
        // Read the re-entry flag BEFORE the Finish handler flips welcome_completed. On a genuine first
        // run it is still false, so the preset always applies; on a Settings re-run an unchanged profile
        // keeps user-tuned settings and a changed one is confirmed first.
        val reentry = isWelcomeCompleted()
        // Persist on the application scope so the profile save + preset write survive the Activity
        // finishing right after Finish; viewModelScope is cancelled by finish() mid-apply, which would
        // drop first-run settings (observed as a JobCancellationException from the preset use case).
        applicationScope.launch(exceptionHandler) {
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

            val previousType = deviceProfileRepository.getCurrentProfile().first().type

            val profile = DeviceProfile(
                type = finalType,
                source = finalSource,
                confidence = finalConfidence,
                presetVersion = 0,
                appliedAtInstallTime = finalType != DeviceProfileType.OTHER,
                lastModified = System.currentTimeMillis()
            )

            deviceProfileRepository.saveProfile(profile)
            Timber.i("Device profile saved on welcome flow completion: $profile (isSkipped=$isSkipped, reentry=$reentry)")

            when {
                // First run: the settings preset was already applied early (applyFirstRunPresetFor-
                // SelectedProfile) so the onboarding pages render the profile defaults and the user's
                // later toggle deviations survive. Re-applying settings here would clobber them, so only
                // ensure the preset ran (covers the Enable-all path, which skips the pages) and record
                // the bookkeeping on the now-saved profile.
                !reentry -> {
                    if (firstRunPresetAppliedFor != finalType) {
                        applyProfilePresetSettings(finalType)
                    }
                    if (firstRunPresetHadOverrides) {
                        applyProfilePresetUseCase.markPresetApplied(presetVersion = 1)
                            .onFailure { Timber.e(it, "WelcomeViewModel: failed to mark device-profile preset applied") }
                    }
                }
                // Re-entry, profile unchanged: skip the preset so the re-run keeps tuned settings.
                finalType == previousType ->
                    Timber.i("Welcome re-entry with unchanged profile - preset apply skipped")
                // Re-entry, profile changed: confirm before overwriting settings (Settings warn parity).
                else -> sendEvent(WelcomeEvent.ConfirmProfilePresetReapply(finalType))
            }
        }
    }

    /** Apply the device-profile preset and, when the profile implies the All Files resource, ensure
     *  that resource exists. Shared by the first-run path and the confirmed re-entry reapply. */
    private suspend fun applyProfilePreset(finalType: DeviceProfileType) {
        applyProfilePresetUseCase.apply(finalType, presetVersion = 1)
            .onSuccess {
                if (profileImpliesAllFilesUseCase(finalType)) {
                    ensureAllFilesPredefinedResourceUseCase()
                        .onFailure { Timber.e(it, "WelcomeViewModel: failed to ensure All Files resource") }
                }
            }
            .onFailure { Timber.e(it, "WelcomeViewModel: failed to apply profile preset") }
    }

    /** Apply the preset for [finalType] after the user confirms the re-entry overwrite warning.
     *  Runs on the application scope so the write completes even if the screen closes mid-apply. */
    fun confirmProfilePresetReapply(finalType: DeviceProfileType) {
        applicationScope.launch(exceptionHandler) {
            applyProfilePreset(finalType)
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
            // S0876: transform overload (mutex-serialized read+write) - this fires from the
            // default-player page, reachable while the enable-all flow's deliverable-install writers
            // are still in flight; a plain getSettings().first()+updateSettings() pair here could
            // race them. The old manual "skip if unchanged" guard is redundant - the transform target
            // (updateSettings(AppSettings)) already no-ops on an equal snapshot (S0018).
            settingsRepository.updateSettings { it.copy(isPrimaryMediaPlayer = true) }
        }
    }

    /** Persist the chosen colour theme ("AUTO"|"LIGHT"|"DARK") to the canonical DataStore settings.
     *  The synchronous startup mirror (ColorThemePrefs) is written by the Activity, mirroring the
     *  Settings split where the UI layer owns the mirror and the ViewModel owns DataStore. */
    fun saveColorTheme(value: String) {
        viewModelScope.launch {
            // S0876: transform overload (mutex-serialized read+write) - the theme picker is reachable
            // throughout onboarding, overlapping the enable-all flow's deliverable-install writers.
            settingsRepository.updateSettings { it.copy(colorTheme = value) }
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

    /**
     * First install only: seed the default left-edge gesture bindings once so enabling gestures from
     * onboarding is immediately useful. Gated by welcome-completed (upgrade users skip onboarding and
     * keep their own configuration) plus a one-shot marker (safe across config/process recreation).
     */
    private fun maybeSeedDefaultGestureBindings() {
        if (isWelcomeCompleted() || isGestureDefaultsSeeded()) return
        applicationScope.launch(exceptionHandler) {
            seedDefaultGestureBindingsUseCase()
            markGestureDefaultsSeeded()
        }
    }

    private fun isGestureDefaultsSeeded(): Boolean {
        return StrictModeHelper.allowDiskReads {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_GESTURE_DEFAULTS_SEEDED, false)
        }
    }

    private fun markGestureDefaultsSeeded() {
        StrictModeHelper.allowDiskWrites {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_GESTURE_DEFAULTS_SEEDED, true)
                .apply()
        }
    }
}

data class WelcomeState(
    val recommendedProfile: DeviceProfileType? = null,
    val selectedProfile: DeviceProfileType? = null,
    val detectorConfidence: DetectionConfidence = DetectionConfidence.NONE
)

sealed class WelcomeEvent {
    /** Re-entry from Settings changed the device profile; ask before overwriting tuned settings. */
    data class ConfirmProfilePresetReapply(val type: DeviceProfileType) : WelcomeEvent()
}
