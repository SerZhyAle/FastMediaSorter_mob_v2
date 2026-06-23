package com.sza.fastmediasorter.ui.applaunchpanel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileUi
import com.sza.fastmediasorter.domain.usecase.panel.LaunchAppLaunchPanelTileUseCase
import com.sza.fastmediasorter.domain.usecase.panel.ResolveAppLaunchPanelTilesUseCase
import com.sza.fastmediasorter.domain.usecase.panel.SeedDefaultAppLaunchPanelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Outcome of tapping a panel tile: launch the target, or route an empty slot to the Edit screen. */
enum class PanelResult { LAUNCH, EDIT }

/**
 * Drives the quick-launch panel: seeds the default tiles on first open, exposes the resolved 15-slot
 * grid, and launches a tapped tile. Holds no Android UI types beyond what the UseCases expose (Rule 3).
 */
@HiltViewModel
class AppLaunchPanelViewModel @Inject constructor(
    resolveTiles: ResolveAppLaunchPanelTilesUseCase,
    private val launchTile: LaunchAppLaunchPanelTileUseCase,
    private val seed: SeedDefaultAppLaunchPanelUseCase,
) : ViewModel() {

    val tiles: StateFlow<List<AppLaunchPanelTileUi>> =
        resolveTiles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { seed() }
    }

    /** An empty slot invites configuration (route to Edit); a filled tile launches its target. */
    fun onTileSelected(tile: AppLaunchPanelTileUi): PanelResult =
        if (tile.isEmpty) PanelResult.EDIT else PanelResult.LAUNCH

    fun launch(tile: AppLaunchPanelTileUi): Boolean = launchTile.launch(tile)
}
