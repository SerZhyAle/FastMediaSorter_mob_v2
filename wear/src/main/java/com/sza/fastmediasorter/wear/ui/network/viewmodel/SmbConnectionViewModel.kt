package com.sza.fastmediasorter.wear.ui.network.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for SMB connection management.
 */
@HiltViewModel
class SmbConnectionViewModel @Inject constructor(
    private val networkSourceRepository: NetworkSourceRepository,
    private val smbDataSource: SmbDataSource
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SmbConnectionUiState())
    val uiState: StateFlow<SmbConnectionUiState> = _uiState.asStateFlow()
    
    fun setServer(server: String) {
        _uiState.value = _uiState.value.copy(server = server, statusMessage = "", isError = false)
    }
    
    fun setShareName(shareName: String) {
        _uiState.value = _uiState.value.copy(shareName = shareName, statusMessage = "", isError = false)
    }
    
    fun setUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username, statusMessage = "", isError = false)
    }
    
    fun setPassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, statusMessage = "", isError = false)
    }
    
    /**
     * Test SMB connection with current credentials.
     */
    fun testConnection() {
        val currentState = _uiState.value
        
        if (currentState.server.isEmpty() || currentState.shareName.isEmpty()) {
            _uiState.value = currentState.copy(
                statusMessage = "Server and share name required",
                isError = true
            )
            return
        }
        
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                _uiState.value = currentState.copy(isLoading = true, statusMessage = "Testing connection...")
            }
            
            val testSource = NetworkSource(
                type = NetworkSourceType.SMB,
                name = currentState.server,
                server = currentState.server,
                port = 445,
                username = currentState.username.ifEmpty { "guest" },
                password = currentState.password,
                shareName = currentState.shareName
            )
            
            val result = withContext(Dispatchers.IO) {
                smbDataSource.connect(testSource)
            }
            
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "Connection successful!",
                        isError = false
                    )
                    Timber.d("SMB connection test successful")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "Connection failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}",
                        isError = true
                    )
                    Timber.e(result.exceptionOrNull(), "SMB connection test failed")
                }
            }
            
            withContext(Dispatchers.IO) {
                smbDataSource.disconnect()
            }
        }
    }
    
    /**
     * Save SMB connection to repository.
     */
    fun saveConnection(onSuccess: () -> Unit = {}) {
        val currentState = _uiState.value
        
        if (currentState.server.isEmpty() || currentState.shareName.isEmpty()) {
            _uiState.value = currentState.copy(
                statusMessage = "Server and share name required",
                isError = true
            )
            return
        }
        
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                _uiState.value = currentState.copy(isLoading = true, statusMessage = "Saving...")
            }
            
            try {
                val networkSource = NetworkSource(
                    type = NetworkSourceType.SMB,
                    name = "${currentState.server}/${currentState.shareName}",
                    server = currentState.server,
                    port = 445,
                    username = currentState.username.ifEmpty { "guest" },
                    password = currentState.password,
                    shareName = currentState.shareName
                )
                
                withContext(Dispatchers.IO) {
                    networkSourceRepository.addSource(networkSource)
                }
                
                withContext(Dispatchers.Main) {
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        statusMessage = "Connection saved!",
                        isError = false
                    )
                    Timber.d("SMB connection saved: ${networkSource.name}")
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        statusMessage = "Failed to save: ${e.message}",
                        isError = true
                    )
                }
                Timber.e(e, "Failed to save SMB connection")
            }
        }
    }
}
