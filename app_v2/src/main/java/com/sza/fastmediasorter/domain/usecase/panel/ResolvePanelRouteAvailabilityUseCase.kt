package com.sza.fastmediasorter.domain.usecase.panel

import android.content.Context
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Answers, per feature route key, whether the route is compiled into this build and whether it is
 * enabled at runtime (strategic S0663 §5.1.B). Build/runtime availability is sourced only from the
 * existing single sources of truth - [CapabilityAvailability] for compile-time capability flags and
 * [SettingsRepository] for runtime toggles - never from build flags directly (CLAUDE.md Rule 15).
 */
class ResolvePanelRouteAvailabilityUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capability: CapabilityAvailability,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * [availableInBuild] - feature is compiled into this flavor. [enabledAtRuntime] - a runtime
     * toggle (where one exists) is on. A route is launchable only when both hold; a compiled but
     * disabled route routes to its setting instead of dead-launching (§6.1).
     */
    data class Availability(val availableInBuild: Boolean, val enabledAtRuntime: Boolean) {
        val isLaunchable: Boolean get() = availableInBuild && enabledAtRuntime
    }

    suspend operator fun invoke(routeKey: String): Availability = withContext(Dispatchers.IO) {
        resolve(routeKey, settingsEnabledGame(), settingsEnabledFavorites())
    }

    /** Availability for every catalog route in one settings read (used by the picker and the seed). */
    suspend fun all(): Map<String, Availability> = withContext(Dispatchers.IO) {
        val gameEnabled = settingsEnabledGame()
        val favoritesEnabled = settingsEnabledFavorites()
        InternalRouteCatalog.all().associate { route ->
            route.key to resolve(route.key, gameEnabled, favoritesEnabled)
        }
    }

    private fun resolve(routeKey: String, gameEnabled: Boolean, favoritesEnabled: Boolean): Availability =
        when (routeKey) {
            InternalRouteCatalog.KEY_CALCULATOR -> Availability(availableInBuild = true, enabledAtRuntime = true)
            InternalRouteCatalog.KEY_GAME -> Availability(availableInBuild = true, enabledAtRuntime = gameEnabled)
            InternalRouteCatalog.KEY_OCR -> Availability(capability.isOcrAvailable(context), enabledAtRuntime = true)
            InternalRouteCatalog.KEY_STREAMS -> Availability(capability.isStreamsAvailable(), enabledAtRuntime = true)
            InternalRouteCatalog.KEY_FAVORITES -> Availability(availableInBuild = true, enabledAtRuntime = favoritesEnabled)
            else -> Availability(availableInBuild = false, enabledAtRuntime = false)
        }

    private suspend fun settingsEnabledGame(): Boolean = settingsRepository.getSettings().first().embeddedGameEnabled

    private suspend fun settingsEnabledFavorites(): Boolean = settingsRepository.getSettings().first().enableFavorites
}
