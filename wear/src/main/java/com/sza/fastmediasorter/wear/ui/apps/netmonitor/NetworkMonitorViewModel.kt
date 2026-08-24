package com.sza.fastmediasorter.wear.ui.apps.netmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSnapshot
import com.sza.fastmediasorter.wear.domain.netmonitor.sectionsFor
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkMonitorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Publishes the watch's readings for as long as the screen observes them and no longer.
 *
 * `WhileSubscribed` with no grace period is the whole cost policy of this program: the flow's
 * `awaitClose` tears the platform callbacks down the moment the last collector leaves, so a program
 * the user walked away from measures nothing.
 */
@HiltViewModel
class NetworkMonitorViewModel @Inject constructor(
    private val repository: WearNetworkMonitorRepository
) : ViewModel() {

    private val sections = sectionsFor(repository.capabilities())

    /** Session-only, newest last. Written by the single collector below and by nothing else. */
    private val history = mutableListOf<WearNetworkSnapshot>()

    private val initialState = NetworkMonitorUiState(
        sections = sections,
        permissionsMissing = !repository.permissionsGranted()
    )

    val uiState: StateFlow<NetworkMonitorUiState> = repository.snapshots()
        .map { snapshot -> record(snapshot) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initialState)

    private fun record(snapshot: WearNetworkSnapshot): NetworkMonitorUiState {
        history.add(snapshot)
        if (history.size > HISTORY_LIMIT) {
            history.removeAt(0)
        }
        return NetworkMonitorUiState(
            sections = sections,
            snapshot = snapshot,
            permissionsMissing = !repository.permissionsGranted(),
            history = history.toList()
        )
    }

    private companion object {
        /** A watch screen shows a handful of past readings; keeping more would only cost memory. */
        const val HISTORY_LIMIT = 20
    }
}
