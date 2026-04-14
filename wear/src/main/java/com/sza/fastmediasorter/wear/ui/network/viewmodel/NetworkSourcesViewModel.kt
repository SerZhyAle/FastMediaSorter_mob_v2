package com.sza.fastmediasorter.wear.ui.network.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.ui.network.SourceItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

sealed class SyncState {
    data object Idle : SyncState()
    data object Pending : SyncState()
    data class Success(val added: Int, val updated: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

/**
 * ViewModel for managing network sources list.
 * Handles loading and displaying available SMB connections and requesting sync from phone.
 */
@HiltViewModel
class NetworkSourcesViewModel @Inject constructor(
    private val networkSourceRepository: NetworkSourceRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<NetworkSourcesUiState>(NetworkSourcesUiState.Loading)
    val uiState: StateFlow<NetworkSourcesUiState> = _uiState.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        Timber.d("NetworkSourcesViewModel initialized")
        loadSources()
        observeSyncResults()
    }

    private fun observeSyncResults() {
        viewModelScope.launch {
            com.sza.fastmediasorter.wear.data.wear.WatchSyncEvents.importResultFlow.collect { result ->
                _syncState.value = SyncState.Success(result.added, result.updated)
                loadSources()
            }
        }
    }

    fun requestSyncFromPhone() {
        viewModelScope.launch {
            _syncState.value = SyncState.Pending
            try {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                if (nodes.isEmpty()) {
                    _syncState.value = SyncState.Error("No phone connected")
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
                _syncState.value = SyncState.Error(e.message ?: "Sync request failed")
            }
        }
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    fun loadSources() {
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
                            server = source.server
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
}
