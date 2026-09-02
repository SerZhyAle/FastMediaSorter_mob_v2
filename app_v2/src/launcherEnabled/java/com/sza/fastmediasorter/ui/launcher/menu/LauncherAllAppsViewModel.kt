package com.sza.fastmediasorter.ui.launcher.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import com.sza.fastmediasorter.domain.model.launcher.InstalledAppSortOrder
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.apps.QueryAllAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S1401: state of the full-screen all-apps list.
 *
 * The chosen order is a stored preference and the typed query is not: the order survives a process
 * death, the search text deliberately does not (strategic 5.1).
 */
@HiltViewModel
class LauncherAllAppsViewModel @Inject constructor(
    private val queryAllApps: QueryAllAppsUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val settings = settingsRepository.getSettings()

    val order: StateFlow<InstalledAppSortOrder> = settings
        .map { InstalledAppSortOrder.fromNameOrDefault(it.allAppsSortOrder) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), DEFAULT_ORDER)

    val descending: StateFlow<Boolean> = settings
        .map { it.allAppsSortDescending }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), false)

    /**
     * S2304: the panel reads its four swipe slots from here rather than opening a second settings flow.
     *
     * Eagerly, unlike the flows above: this one is read synchronously the moment a swipe is recognized
     * and never collected, so a lazily started share would answer every gesture with the defaults.
     */
    val appSettings: StateFlow<AppSettings> = settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    @OptIn(ExperimentalCoroutinesApi::class)
    val apps: StateFlow<List<InstalledApp>> =
        combine(_query, order, descending) { query, order, descending -> Triple(query, order, descending) }
            .flatMapLatest { (query, order, descending) -> queryAllApps(query, order, descending) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setOrder(value: InstalledAppSortOrder) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.withLauncher { copy(allAppsSortOrder = value.name) } }
        }
    }

    fun toggleDirection() {
        viewModelScope.launch {
            settingsRepository.updateSettings { s ->
                s.withLauncher { copy(allAppsSortDescending = !allAppsSortDescending) }
            }
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        val DEFAULT_ORDER = InstalledAppSortOrder.LABEL
    }
}
