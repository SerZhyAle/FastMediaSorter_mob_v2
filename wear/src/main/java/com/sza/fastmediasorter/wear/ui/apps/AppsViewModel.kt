package com.sza.fastmediasorter.wear.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * Turns the view-mode preference into the program list the Apps screen renders.
 *
 * The program set itself is not observed: it is fixed at build time, so only the view mode can change
 * under the screen while it is open.
 */
@HiltViewModel
class AppsViewModel @Inject constructor(
    preferencesRepository: WearPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<AppsUiState> = preferencesRepository.viewMode
        .map { viewMode -> AppsUiState(apps = WearAppCatalog.apps(), viewMode = viewMode) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = AppsUiState()
        )
}
