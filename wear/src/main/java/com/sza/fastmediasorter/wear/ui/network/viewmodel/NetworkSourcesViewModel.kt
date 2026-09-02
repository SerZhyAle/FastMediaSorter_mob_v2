package com.sza.fastmediasorter.wear.ui.network.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.ExportSourcesUseCase
import com.sza.fastmediasorter.wear.ui.network.SourceItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

private const val VIEW_MODE_SUBSCRIPTION_MS = 5_000L

sealed class SyncState {
    data object Idle : SyncState()
    data object Pending : SyncState()
    data class Success(val added: Int, val updated: Int) : SyncState()

    /**
     * S2275: a resource id rather than a String. The two failures this state carried were an English
     * literal and a raw exception message, and both reached the watch screen untranslated - the screen
     * a Play reviewer lands on when no phone answers.
     */
    data class Error(@StringRes val messageRes: Int) : SyncState()
}

/** S1833: the outcome of checking a source that is already saved, shown over the list. */
sealed class ConnectionTestState {
    data object Idle : ConnectionTestState()
    data class Testing(val sourceName: String) : ConnectionTestState()
    data class Finished(val sourceName: String, val message: String, val isError: Boolean) : ConnectionTestState()
}

sealed class ExportState {
    data object Idle : ExportState()
    data object Exporting : ExportState()
    data class Success(val count: Int) : ExportState()
    data class Error(val message: String) : ExportState()
}

/**
 * ViewModel for managing network sources list.
 * Handles loading and displaying available SMB connections and requesting sync from phone.
 */
@HiltViewModel
class NetworkSourcesViewModel @Inject constructor(
    private val networkSourceRepository: NetworkSourceRepository,
    @ApplicationContext private val context: Context,
    private val exportSourcesUseCase: ExportSourcesUseCase,
    private val preferencesRepository: WearPreferencesRepository
) : ViewModel() {

    /** S1781: ADR-1 - one stored view shared with the home screen, never a second setting here. */
    val viewMode: StateFlow<WearViewMode> = preferencesRepository.viewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(VIEW_MODE_SUBSCRIPTION_MS), WearViewMode.LIST)

    private val _uiState = MutableStateFlow<NetworkSourcesUiState>(NetworkSourcesUiState.Loading)
    val uiState: StateFlow<NetworkSourcesUiState> = _uiState.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    init {
        Timber.d("NetworkSourcesViewModel initialized")
        observeSources()
        observeSyncResults()
    }

    private fun observeSources() {
        viewModelScope.launch {
            networkSourceRepository.observeSources()
                .catch { e ->
                    Timber.e(e, "Error observing network sources")
                    _uiState.value = NetworkSourcesUiState.Error(
                        message = e.message ?: "Failed to load network sources"
                    )
                }
                .collect { allSources ->
                    _uiState.value = if (allSources.isEmpty()) {
                        Timber.d("No network sources found")
                        NetworkSourcesUiState.Empty
                    } else {
                        val sourceItems = allSources.map { source ->
                            SourceItem(
                                id = source.id,
                                name = source.name,
                                server = source.server,
                                type = source.type,
                                iconId = source.iconId
                            )
                        }
                        Timber.d("Observed ${sourceItems.size} network sources")
                        NetworkSourcesUiState.Success(sourceItems)
                    }
                }
        }
    }

    private fun observeSyncResults() {
        viewModelScope.launch {
            com.sza.fastmediasorter.wear.data.wear.WatchSyncEvents.importResultFlow.collect { result ->
                _syncState.value = SyncState.Success(result.added, result.updated)
            }
        }
    }

    fun requestSyncFromPhone() {
        viewModelScope.launch {
            _syncState.value = SyncState.Pending
            try {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                if (nodes.isEmpty()) {
                    _syncState.value = SyncState.Error(R.string.wear_sync_no_phone_paired)
                    Timber.w("requestSyncFromPhone: no connected nodes")
                    return@launch
                }
                val nodeId = nodes.first().id
                Wearable.getMessageClient(context)
                    .sendMessage(nodeId, "/fms/network_sources/request", ByteArray(0))
                    .await()
                Timber.d("Sync request sent to node $nodeId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to request sync from phone")
                _syncState.value = SyncState.Error(R.string.wear_sync_request_failed)
            }
        }
    }

    fun exportToPhone() {
        _exportState.value = ExportState.Exporting
        viewModelScope.launch {
            exportSourcesUseCase()
                .onSuccess { count -> _exportState.value = ExportState.Success(count) }
                .onFailure { e -> _exportState.value = ExportState.Error(e.message ?: "Export failed") }
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    fun retryLoad() {
        viewModelScope.launch {
            _uiState.value = NetworkSourcesUiState.Loading
            
            try {
                val allSources = networkSourceRepository.getAllSources()
                
                if (allSources.isEmpty()) {
                    _uiState.value = NetworkSourcesUiState.Empty
                    Timber.d("No network sources found")
                } else {
                    val sourceItems = allSources.map { source ->
                        SourceItem(
                            id = source.id,
                            name = source.name,
                            server = source.server,
                            type = source.type,
                            iconId = source.iconId
                        )
                    }
                    _uiState.value = NetworkSourcesUiState.Success(sourceItems)
                    Timber.d("Loaded ${sourceItems.size} network sources")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading network sources")
                _uiState.value = NetworkSourcesUiState.Error(
                    message = e.message ?: "Failed to load network sources"
                )
            }
        }
    }

    /**
     * S1781: the home screen's Last used section reads this, so it is written where a resource is
     * opened. S1836: the identifier travels with the name, because the section addresses the source
     * rather than merely captioning it.
     */
    fun rememberLastUsedResource(id: String, name: String) {
        viewModelScope.launch {
            preferencesRepository.setLastUsedResource(id, name)
        }
    }

    /**
     * S1833: the check a saved source never had. Until now the test button lived inside the add form
     * and could only judge what was typed there, so a source that stopped answering could not be
     * checked at all. The probe is the repository call that form already makes, so a source is judged
     * the same way whether it is being typed or has been stored for months.
     */
    fun testSource(id: String, name: String) {
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing(name)
            val source = networkSourceRepository.getSourceById(id)
            if (source == null) {
                Timber.w("Saved source $id vanished before its connection test could run")
                _connectionTestState.value = ConnectionTestState.Finished(
                    sourceName = name,
                    message = context.getString(R.string.unknown_error),
                    isError = true
                )
                return@launch
            }
            val result = networkSourceRepository.testConnection(source)
            val succeeded = result.isSuccess && result.getOrDefault(false)
            _connectionTestState.value = ConnectionTestState.Finished(
                sourceName = name,
                message = if (succeeded) {
                    context.getString(R.string.connection_successful)
                } else {
                    failureMessage(result.exceptionOrNull())
                },
                isError = !succeeded
            )
        }
    }

    private fun failureMessage(failure: Throwable?): String {
        return if (failure is UnsupportedOperationException) {
            context.getString(R.string.connection_test_not_supported)
        } else {
            context.getString(
                R.string.connection_failed_with_reason,
                failure?.message ?: context.getString(R.string.unknown_error)
            )
        }
    }

    fun resetConnectionTestState() {
        _connectionTestState.value = ConnectionTestState.Idle
    }

    fun deleteSource(id: String) {
        viewModelScope.launch {
            try {
                networkSourceRepository.deleteSource(id)
                Timber.d("Deleted source $id")
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete source $id")
            }
        }
    }
}
