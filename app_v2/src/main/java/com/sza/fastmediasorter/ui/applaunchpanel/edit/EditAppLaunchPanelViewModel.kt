package com.sza.fastmediasorter.ui.applaunchpanel.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTile
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileType
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileUi
import com.sza.fastmediasorter.domain.model.panel.AppLaunchPanelRouteTarget
import com.sza.fastmediasorter.domain.repository.AppLaunchPanelRepository
import com.sza.fastmediasorter.domain.usecase.panel.ResetAppLaunchPanelUseCase
import com.sza.fastmediasorter.domain.usecase.panel.ResolveAppLaunchPanelTilesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the resolved 15-slot Edit-panel grid and applies slot mutations through the repository.
 * All persistence runs in [viewModelScope]; the Activity only renders state and forwards intents.
 */
@HiltViewModel
class EditAppLaunchPanelViewModel @Inject constructor(
    resolveTiles: ResolveAppLaunchPanelTilesUseCase,
    private val repository: AppLaunchPanelRepository,
    private val resetPanelUseCase: ResetAppLaunchPanelUseCase,
) : ViewModel() {

    val tiles: StateFlow<List<AppLaunchPanelTileUi>> =
        resolveTiles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Pins [packageName] as an external-app tile in [slot], overwriting any existing tile there. */
    fun addAppToSlot(slot: Int, packageName: String) {
        viewModelScope.launch {
            repository.setTile(
                AppLaunchPanelTile(
                    slotIndex = slot,
                    type = AppLaunchPanelTileType.EXTERNAL_APP,
                    targetId = packageName,
                    labelOverride = null,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Pins one of our own features as an INTERNAL_ROUTE tile in [slot] (`fn:<routeKey>`). */
    fun addInternalFeatureToSlot(slot: Int, routeKey: String) {
        persistRouteTile(slot, AppLaunchPanelRouteTarget.Feature(routeKey))
    }

    /** Pins a curated OS shortcut as an INTERNAL_ROUTE tile in [slot] (`os:<targetKey>`). */
    fun addOsShortcutToSlot(slot: Int, targetKey: String) {
        persistRouteTile(slot, AppLaunchPanelRouteTarget.OsShortcut(targetKey))
    }

    /** Pins a specific resource as an INTERNAL_ROUTE tile in [slot] (`resource:<id>`). */
    fun addResourceToSlot(slot: Int, resourceId: Long) {
        persistRouteTile(slot, AppLaunchPanelRouteTarget.Resource(resourceId))
    }

    private fun persistRouteTile(slot: Int, target: AppLaunchPanelRouteTarget) {
        viewModelScope.launch {
            repository.setTile(
                AppLaunchPanelTile(
                    slotIndex = slot,
                    type = AppLaunchPanelTileType.INTERNAL_ROUTE,
                    targetId = target.encode(),
                    labelOverride = null,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun removeTile(slot: Int) {
        viewModelScope.launch { repository.removeTile(slot) }
    }

    /** Clears every slot and re-applies the install-time default set (S0663). */
    fun resetPanel() {
        viewModelScope.launch { resetPanelUseCase() }
    }

    fun moveTile(from: Int, to: Int) {
        if (from == to) return
        viewModelScope.launch { repository.moveTile(from, to) }
    }
}
