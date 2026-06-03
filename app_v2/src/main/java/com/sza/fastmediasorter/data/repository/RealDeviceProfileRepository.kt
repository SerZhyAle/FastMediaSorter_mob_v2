package com.sza.fastmediasorter.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.data.datasource.DeviceProfileLocalDataSource
import com.sza.fastmediasorter.data.model.DeviceProfile
import com.sza.fastmediasorter.data.model.DeviceProfileSource
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.data.model.DetectionConfidence
import com.sza.fastmediasorter.data.model.DetectorSignal
import com.sza.fastmediasorter.domain.detector.DeviceProfileDetector
import com.sza.fastmediasorter.domain.repository.DeviceProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber

/**
 * Real implementation of device profile repository.
 * Handles:
 * - Fresh install: detector auto-detection or manual choice in Welcome
 * - Existing install migration: set to "Other" without applying preset
 * - Profile updates via Settings
 */
@Singleton
class RealDeviceProfileRepository @Inject constructor(
    private val detector: DeviceProfileDetector,
    private val localDataSource: DeviceProfileLocalDataSource,
    @ApplicationContext private val context: Context
) : DeviceProfileRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "${context.packageName}_preferences",
        Context.MODE_PRIVATE
    )
    private val welcomePrefs: SharedPreferences = context.getSharedPreferences(
        "welcome_prefs",
        Context.MODE_PRIVATE
    )

    init {
        // On first run, handle migration or fresh install
        initializeMigrationIfNeeded()
    }

    /**
     * One-shot first-run bootstrap. Runs entirely on Dispatchers.IO so the SharedPreferences
     * reads and the Room write never touch the construction (possibly main) thread - this avoids
     * a StrictMode disk-read violation when this @Singleton is first injected on the main thread.
     */
    private fun initializeMigrationIfNeeded() {
        CoroutineScope(Dispatchers.IO).launch {
            if (prefs.getBoolean("device_profile_initialized", false)) return@launch

            if (welcomePrefs.getBoolean("welcome_completed", false)) {
                Timber.d("S0327: existing-install migration to OTHER profile")
                // Existing install: migrate to "Other" without applying any preset.
                val profile = DeviceProfile(
                    type = DeviceProfileType.OTHER,
                    source = DeviceProfileSource.MIGRATION_EXISTING,
                    confidence = DetectionConfidence.NONE,
                    presetVersion = 0,
                    appliedAtInstallTime = false,
                    lastModified = System.currentTimeMillis()
                )
                saveProfile(profile)
                Timber.i("Existing install migrated to OTHER device profile, no preset applied")
            }
            // Fresh install: profile will be set by the Welcome detector/selector.
            prefs.edit().putBoolean("device_profile_initialized", true).apply()
        }
    }

    override fun getCurrentProfile(): Flow<DeviceProfile> {
        return localDataSource.observeProfile().map { profile ->
            profile ?: getDefaultProfile()
        }
    }

    override suspend fun saveProfile(profile: DeviceProfile): Result<Unit> = runCatching {
        localDataSource.saveProfile(profile)
    }

    override suspend fun updatePresetApplied(presetVersion: Int): Result<Unit> = runCatching {
        val current = getCurrentProfile().first()
        localDataSource.saveProfile(
            current.copy(
                presetVersion = presetVersion,
                appliedAtInstallTime = true,
                lastModified = System.currentTimeMillis()
            )
        )
        Timber.i("Device profile preset version marked as applied: version=$presetVersion profile=${current.type}")
    }

    override suspend fun resetToDefault(fallbackProfile: DeviceProfileType): Result<Unit> = runCatching {
        val profile = DeviceProfile(
            type = fallbackProfile,
            source = DeviceProfileSource.AUTO_DETECTED,
            confidence = DetectionConfidence.LOW,
            presetVersion = 0,
            appliedAtInstallTime = false,
            lastModified = System.currentTimeMillis()
        )
        localDataSource.saveProfile(profile)
    }

    override fun getDetectionHistory(): Flow<List<DetectorSignal>> {
        return emptyFlow()
    }

    override suspend fun isMigrationExisting(): Boolean {
        return getCurrentProfile().first().source == DeviceProfileSource.MIGRATION_EXISTING
    }

    private fun getDefaultProfile(): DeviceProfile {
        return DeviceProfile(
            type = DeviceProfileType.PERSONAL_SMARTPHONE,
            source = DeviceProfileSource.AUTO_DETECTED,
            confidence = DetectionConfidence.LOW,
            presetVersion = 0,
            appliedAtInstallTime = false,
            lastModified = System.currentTimeMillis()
        )
    }
}
