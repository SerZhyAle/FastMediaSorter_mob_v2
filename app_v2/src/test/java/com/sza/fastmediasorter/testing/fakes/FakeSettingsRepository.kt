package com.sza.fastmediasorter.testing.fakes

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.model.TouchZoneHintType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-written fake for [SettingsRepository].
 *
 * The current settings are exposed through [settingsFlow] (reactive) and as the mutable
 * [currentSettings]. Touch-zone hint flags and the resource-grid flag are stored in plain backing
 * maps/fields. Invocations a test typically asserts on are recorded below. No real I/O, no logging.
 */
class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {

    private val settingsFlow = MutableStateFlow(initial)

    /** Convenience accessor for the latest persisted settings. */
    val currentSettings: AppSettings
        get() = settingsFlow.value

    private var playerFirstRun: Boolean = true
    private val touchZoneHints: MutableMap<TouchZoneHintType, Boolean> = mutableMapOf()

    // Invocation records ---------------------------------------------------
    val updatedSettings: MutableList<AppSettings> = mutableListOf()
    var resetToDefaultsCalled: Boolean = false
    var resetAllTouchZoneHintsCalled: Boolean = false

    /** Directly seed the emitted settings without recording an update. */
    fun setSettings(settings: AppSettings) {
        settingsFlow.value = settings
    }

    override fun getSettings(): Flow<AppSettings> = settingsFlow

    override suspend fun updateSettings(settings: AppSettings) {
        updatedSettings.add(settings)
        settingsFlow.value = settings
    }

    override suspend fun resetToDefaults() {
        resetToDefaultsCalled = true
        settingsFlow.value = AppSettings()
    }

    override suspend fun setPlayerFirstRun(isFirstRun: Boolean) {
        playerFirstRun = isFirstRun
    }

    override suspend fun isPlayerFirstRun(): Boolean = playerFirstRun

    override suspend fun saveLastUsedResourceId(resourceId: Long) {
        settingsFlow.value = settingsFlow.value.copy(lastUsedResourceId = resourceId)
    }

    override suspend fun getLastUsedResourceId(): Long = settingsFlow.value.lastUsedResourceId

    override suspend fun setResourceGridMode(isGridMode: Boolean) {
        settingsFlow.value = settingsFlow.value.copy(isResourceGridMode = isGridMode)
    }

    override suspend fun updateEmbeddedGameEnabled(enabled: Boolean) {
        updateSettings(settingsFlow.value.copy(embeddedGameEnabled = enabled))
    }

    override suspend fun updateScheduledOperationsPaused(paused: Boolean) {
        updateSettings(settingsFlow.value.copy(scheduledOperationsPaused = paused))
    }

    override suspend fun isTouchZoneHintShown(type: TouchZoneHintType): Boolean =
        touchZoneHints[type] ?: false

    override suspend fun setTouchZoneHintShown(type: TouchZoneHintType, shown: Boolean) {
        touchZoneHints[type] = shown
    }

    override suspend fun resetAllTouchZoneHints() {
        resetAllTouchZoneHintsCalled = true
        touchZoneHints.clear()
    }
}
