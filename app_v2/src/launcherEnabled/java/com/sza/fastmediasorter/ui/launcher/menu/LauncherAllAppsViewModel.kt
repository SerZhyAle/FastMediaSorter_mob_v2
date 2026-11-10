package com.sza.fastmediasorter.ui.launcher.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import com.sza.fastmediasorter.domain.model.launcher.InstalledAppSortOrder
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.apps.ApplyAllAppsSortDefaultUseCase
import com.sza.fastmediasorter.domain.usecase.apps.CreateAppCategoryUseCase
import com.sza.fastmediasorter.domain.usecase.apps.MoveAppCategoryUseCase
import com.sza.fastmediasorter.domain.usecase.apps.ObserveAppCategoryCatalogUseCase
import com.sza.fastmediasorter.domain.usecase.apps.QueryAllAppsUseCase
import com.sza.fastmediasorter.domain.usecase.apps.SeedAppCategoriesUseCase
import com.sza.fastmediasorter.domain.usecase.apps.SetAppCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
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
    private val settingsRepository: SettingsRepository,
    private val applyAllAppsSortDefault: ApplyAllAppsSortDefaultUseCase,
    observeAppCategoryCatalog: ObserveAppCategoryCatalogUseCase,
    private val seedAppCategories: SeedAppCategoriesUseCase,
    private val setAppCategoriesUseCase: SetAppCategoriesUseCase,
    private val createAppCategory: CreateAppCategoryUseCase,
    private val moveAppCategory: MoveAppCategoryUseCase,
) : ViewModel() {

    init {
        // S2736: here rather than in the deferred startup worker, which runs half a minute after launch
        // and could let this screen open on the order the migration is about to replace.
        viewModelScope.launch { applyAllAppsSortDefault() }
        // S4530: lives as long as the screen, so an app installed while it is open gets its category too.
        viewModelScope.launch { seedAppCategories.run() }
    }

    /** S4530: the groups the category order draws, and every category the chooser offers. */
    val catalog: StateFlow<AppCategoryCatalog> = observeAppCategoryCatalog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), AppCategoryCatalog.EMPTY)

    private val _categorySaveFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** S4530: a write that did not persist is reported, never drawn as done (strategic §3.4). */
    val categorySaveFailed: SharedFlow<Unit> = _categorySaveFailed

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

    fun setAppCategories(packageName: String, categoryIds: Set<String>) {
        Timber.d("S4530: set app categories")
        viewModelScope.launch {
            reportFailure(setAppCategoriesUseCase(packageName, categoryIds))
        }
    }

    fun createCategory(name: String, onCreated: (String) -> Unit) {
        Timber.d("S4530: create category")
        viewModelScope.launch {
            val result = createAppCategory(name)
            result.onSuccess(onCreated)
            reportFailure(result)
        }
    }

    fun moveCategory(categoryId: String, displayedOrder: List<String>, offset: Int) {
        Timber.d("S4530: move category")
        viewModelScope.launch {
            reportFailure(moveAppCategory(categoryId, displayedOrder, offset))
        }
    }

    private fun reportFailure(result: Result<*>) {
        if (result.isFailure) _categorySaveFailed.tryEmit(Unit)
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        val DEFAULT_ORDER = InstalledAppSortOrder.CATEGORY
    }
}
