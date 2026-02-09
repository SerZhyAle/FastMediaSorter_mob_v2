package com.sza.fastmediasorter.wear.ui.network.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.ui.network.SourceItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for managing network sources list.
 * Handles loading and displaying available SMB connections.
 */
@HiltViewModel
class NetworkSourcesViewModel @Inject constructor(
    private val networkSourceRepository: NetworkSourceRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<NetworkSourcesUiState>(NetworkSourcesUiState.Loading)
    val uiState: StateFlow<NetworkSourcesUiState> = _uiState.asStateFlow()
    
    init {
        Timber.d("NetworkSourcesViewModel initialized")
        loadSources()
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
