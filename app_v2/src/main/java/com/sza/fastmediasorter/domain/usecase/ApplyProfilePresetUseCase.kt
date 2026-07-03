package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.xr.VrProfileSettingsSync
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.data.preset.DeviceProfilePresetApplier
import com.sza.fastmediasorter.data.preset.DeviceProfilePresetCsvDataSource
import com.sza.fastmediasorter.domain.repository.DeviceProfileRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * Applies the owner-authored device-profile preset to the current settings.
 *
 * The matrix lives in `assets/device_profile_presets.csv` (one row per [com.sza.fastmediasorter.domain.model.AppSettings]
 * field, one column per profile). Only non-empty cells are overrides; an empty cell keeps the
 * current value (on a fresh install that equals the code default - the intended "empty = default"
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
        return try {
            val hadOverrides = applySettingsOnly(profileType).getOrThrow()
            if (hadOverrides) {
                markPresetApplied(presetVersion).getOrThrow()
                Timber.i("ApplyProfilePresetUseCase: Preset applied successfully for $profileType")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            // Coroutine cancellation is not a failure: never report it as Result.failure (callers log
            // those at error level). Propagate so structured concurrency unwinds correctly.
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Folds the profile's CSV overrides into the current settings (plus VR sync) and writes them,
     * WITHOUT touching the device-profile bookkeeping. Returns whether the profile had any overrides.
     *
     * Welcome applies the settings early - before the user reaches the capability/permission pages - so
     * those pages render the profile's defaults and any change the user then makes there survives to
     * completion. Re-writing the settings at Finish would clobber that deviation, which is why the
     * bookkeeping ([markPresetApplied]) is recorded separately.
     */
    suspend fun applySettingsOnly(profileType: DeviceProfileType): Result<Boolean> {
        val overrides = presetDataSource.load()[profileType].orEmpty()
        return try {
            // S0876: transform overload (mutex-serialized read+write) - Welcome invokes this during
            // onboarding, overlapping the enable-all flow's concurrent deliverable-install writers.
            // The write itself is a no-op when unchanged (S0018 idempotency guard in updateSettings),
            // so the old manual "skip if nothing changed" branch is no longer needed.
            var changed = false
            settingsRepository.updateSettings { current ->
                val presetUpdated = overrides.entries.fold(current) { acc, (field, raw) ->
                    presetApplier.applyOverride(acc, field, raw)
                }
                val updated = vrProfileSettingsSync.align(profileType, presetUpdated)
                changed = updated != current
                updated
            }
            if (!changed) {
                Timber.i("ApplyProfilePresetUseCase: No settings changes required for $profileType")
            }
            Result.success(overrides.isNotEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Records that [presetVersion] was applied to the currently-saved device profile. */
    suspend fun markPresetApplied(presetVersion: Int): Result<Unit> =
        profileRepository.updatePresetApplied(presetVersion)
}
