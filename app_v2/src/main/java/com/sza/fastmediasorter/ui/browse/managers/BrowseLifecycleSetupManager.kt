package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.data.local.preferences.BrowseStateDataStore
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.ClearResumeStateUseCase
import com.sza.fastmediasorter.domain.usecase.GetResumeStateUseCase
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.ui.browse.selection.BrowseSelectionManager
import com.sza.fastmediasorter.ui.browse.undo.BrowseUndoManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class BrowseCacheCleanup(
    val unifiedCache: UnifiedFileCache,
    val hasActiveTransfer: suspend () -> Boolean
)

class BrowseLifecycleSetupDependencies(
    val context: Context,
    val settingsRepository: SettingsRepository,
    val cacheCleanup: BrowseCacheCleanup,
    val stateDependencies: BrowseLifecycleStateDependencies
)

class BrowseLifecycleStateDependencies(
    val browseStateDataStore: BrowseStateDataStore,
    val selectionManager: BrowseSelectionManager,
    val undoManager: BrowseUndoManager,
    val getResumeStateUseCase: GetResumeStateUseCase,
    val clearResumeStateUseCase: ClearResumeStateUseCase,
    val stateFlow: StateFlow<BrowseState>,
    val updateState: ((BrowseState) -> BrowseState) -> Unit,
    val sendEvent: (BrowseEvent) -> Unit,
    val applyFilter: () -> Unit
)

/**
 * Handles ViewModel lifecycle setup: init tasks, settings load, filter restore,
 * state-observer wiring, and startup cleanup.
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition - IV.1).
 */
class BrowseLifecycleSetupManager(
    private val dependencies: BrowseLifecycleSetupDependencies,
    private val windowIdProvider: () -> String,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val exceptionHandler: CoroutineContext,
    private val resourceId: Long
) {
    /** Cached settings; populated on first [initialize] call via [loadSettings]. */
    private var cachedSettings: AppSettings? = null

    val scheduledOperationsEnabled: Boolean
        get() = cachedSettings?.enableScheduledOperations == true

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run all startup tasks. Call once from [BrowseViewModel.init].
     * Order matters: clearPdfThumbnailCache → checkResumeState → loadSettings
     * → restoreFilterState → observe selection/undo.
     */
    fun initialize() {
        clearPdfThumbnailCache()
        checkResumeStateOnInit()
        loadSettings()
        restoreFilterState()
        observeSelectionChanges()
        observeUndoChanges()
    }

    /** Returns current [AppSettings], loading from repository if not yet cached. */
    suspend fun getSettings(): AppSettings {
        return cachedSettings ?: dependencies.settingsRepository.getSettings().first().also {
            cachedSettings = it
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun clearPdfThumbnailCache() {
        scope.launch(ioDispatcher) {
            try {
                if (dependencies.cacheCleanup.hasActiveTransfer()) {
                    Timber.d("S1362: startup cache cleanup skipped during active transfer")
                    Timber.i("BrowseLifecycleSetupManager: skipped cache cleanup during active transfer")
                } else {
                    dependencies.cacheCleanup.unifiedCache.clearAll()
                    Timber.d("BrowseLifecycleSetupManager: Cleared UnifiedFileCache on init")
                }
            } catch (e: Exception) {
                Timber.e(e, "BrowseLifecycleSetupManager: Failed to clear UnifiedFileCache")
            }
        }
    }

    private fun checkResumeStateOnInit() {
        scope.launch {
            try {
                val savedState = dependencies.stateDependencies.getResumeStateUseCase(windowIdProvider())
                if (savedState != null && savedState.resourceId != resourceId) {
                    Timber.d("BrowseLifecycleSetupManager: resource changed (saved=${savedState.resourceId}, current=$resourceId) - clearing resume state")
                    dependencies.stateDependencies.clearResumeStateUseCase(windowIdProvider())
                }
            } catch (e: Exception) {
                Timber.w(e, "BrowseLifecycleSetupManager: Failed to check resume state on init")
            }
        }
    }

    private fun loadSettings() {
        scope.launch(ioDispatcher + exceptionHandler) {
            val settings = dependencies.settingsRepository.getSettings().first()
            cachedSettings = settings
            dependencies.stateDependencies.updateState { it.copy(
                useCompactElements = settings.useCompactElements
            ) }
        }
    }

    private fun restoreFilterState() {
        scope.launch(ioDispatcher) {
            dependencies.stateDependencies.browseStateDataStore.filter.first()?.let { savedFilter ->
                if (!savedFilter.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        dependencies.stateDependencies.updateState { it.copy(filter = savedFilter) }
                        dependencies.stateDependencies.applyFilter()
                        // Keep the restored-filter toast scannable by using short localized labels.
                        val filterDesc = buildString {
                            if (!savedFilter.nameContains.isNullOrBlank()) {
                                append(dependencies.context.getString(R.string.filter_label_name_short))
                            }
                            if (savedFilter.minSizeMb != null) {
                                if (isNotEmpty()) append(", ")
                                append(dependencies.context.getString(R.string.filter_label_size_short))
                            }
                            if (savedFilter.minDate != null) {
                                if (isNotEmpty()) append(", ")
                                append(dependencies.context.getString(R.string.filter_label_date_short))
                            }
                        }
                        if (filterDesc.isNotEmpty()) {
                            dependencies.stateDependencies.sendEvent(
                                BrowseEvent.ShowMessage(
                                    dependencies.context.getString(R.string.msg_last_filter_restored, filterDesc)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun observeSelectionChanges() {
        scope.launch(ioDispatcher + exceptionHandler) {
            dependencies.stateDependencies.selectionManager.selectionState.collect { selection ->
                dependencies.stateDependencies.updateState { state ->
                    state.copy(
                        selectedFiles = selection.selectedFiles,
                        lastSelectedPath = selection.lastSelectedPath
                    )
                }
            }
        }
    }

    private fun observeUndoChanges() {
        scope.launch(ioDispatcher + exceptionHandler) {
            dependencies.stateDependencies.undoManager.undoState.collect { undoState ->
                dependencies.stateDependencies.updateState { state ->
                    state.copy(
                        lastOperation = undoState.lastOperation,
                        undoOperationTimestamp = undoState.undoOperationTimestamp
                    )
                }
            }
        }
    }
}
