package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.preferences.BrowseStateDataStore
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.ClearResumeStateUseCase
import com.sza.fastmediasorter.domain.usecase.GetResumeStateUseCase
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseState
import com.sza.fastmediasorter.ui.browse.selection.BrowseSelectionManager
import com.sza.fastmediasorter.ui.browse.undo.BrowseUndoManager
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Handles ViewModel lifecycle setup: init tasks, settings load, filter restore,
 * state-observer wiring, and startup cleanup.
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition - IV.1).
 */
class BrowseLifecycleSetupManager(
    private val context: Context,
    private val browseStateDataStore: BrowseStateDataStore,
    private val settingsRepository: SettingsRepository,
    private val unifiedCache: com.sza.fastmediasorter.core.cache.UnifiedFileCache,
    private val selectionManager: BrowseSelectionManager,
    private val undoManager: BrowseUndoManager,
    private val getResumeStateUseCase: GetResumeStateUseCase,
    private val clearResumeStateUseCase: ClearResumeStateUseCase,
    private val windowIdProvider: () -> String,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val exceptionHandler: CoroutineContext,
    private val stateFlow: StateFlow<BrowseState>,
    private val updateState: ((BrowseState) -> BrowseState) -> Unit,
    private val sendEvent: (BrowseEvent) -> Unit,
    private val applyFilter: () -> Unit,
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
        return cachedSettings ?: settingsRepository.getSettings().first().also {
            cachedSettings = it
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun clearPdfThumbnailCache() {
        scope.launch(ioDispatcher) {
            try {
                unifiedCache.clearAll()
                Timber.d("BrowseLifecycleSetupManager: Cleared UnifiedFileCache on init")
            } catch (e: Exception) {
                Timber.e(e, "BrowseLifecycleSetupManager: Failed to clear UnifiedFileCache")
            }
        }
    }

    private fun checkResumeStateOnInit() {
        scope.launch {
            try {
                val savedState = getResumeStateUseCase(windowIdProvider())
                if (savedState != null && savedState.resourceId != resourceId) {
                    Timber.d("BrowseLifecycleSetupManager: resource changed (saved=${savedState.resourceId}, current=$resourceId) - clearing resume state")
                    clearResumeStateUseCase(windowIdProvider())
                }
            } catch (e: Exception) {
                Timber.w(e, "BrowseLifecycleSetupManager: Failed to check resume state on init")
            }
        }
    }

    private fun loadSettings() {
        scope.launch(ioDispatcher + exceptionHandler) {
            val settings = settingsRepository.getSettings().first()
            cachedSettings = settings
            updateState { it.copy(
                showSmallControls = settings.showSmallControls,
                useCompactElements = settings.useCompactElements
            ) }
        }
    }

    private fun restoreFilterState() {
        scope.launch(ioDispatcher) {
            browseStateDataStore.filter.first()?.let { savedFilter ->
                if (!savedFilter.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        updateState { it.copy(filter = savedFilter) }
                        applyFilter()
                        // Keep the restored-filter toast scannable by using short localized labels.
                        val filterDesc = buildString {
                            if (!savedFilter.nameContains.isNullOrBlank()) append(context.getString(R.string.filter_label_name_short))
                            if (savedFilter.minSizeMb != null) {
                                if (isNotEmpty()) append(", ")
                                append(context.getString(R.string.filter_label_size_short))
                            }
                            if (savedFilter.minDate != null) {
                                if (isNotEmpty()) append(", ")
                                append(context.getString(R.string.filter_label_date_short))
                            }
                        }
                        if (filterDesc.isNotEmpty()) {
                            sendEvent(BrowseEvent.ShowMessage(context.getString(R.string.msg_last_filter_restored, filterDesc)))
                        }
                    }
                }
            }
        }
    }

    private fun observeSelectionChanges() {
        scope.launch(ioDispatcher + exceptionHandler) {
            selectionManager.selectionState.collect { selection ->
                updateState { state ->
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
            undoManager.undoState.collect { undoState ->
                updateState { state ->
                    state.copy(
                        lastOperation = undoState.lastOperation,
                        undoOperationTimestamp = undoState.undoOperationTimestamp
                    )
                }
            }
        }
    }
}
