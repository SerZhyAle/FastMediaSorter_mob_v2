package com.sza.fastmediasorter.wear.ui.network.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.util.errorUnlessCancellation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AddNetworkSourceViewModel @Inject constructor(
    private val networkSourceRepository: NetworkSourceRepository,
    @ApplicationContext private val context: Context,
    restrictedCapabilities: WearRestrictedCapabilities,
    preferencesRepository: WearPreferencesRepository
) : ViewModel() {

    /**
     * S2486: the same stored view the sources list and the home screen read, so the add form lays out in
     * the number of columns the user already chose instead of staying a single tall list of its own.
     */
    val viewMode: StateFlow<WearViewMode> = preferencesRepository.viewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(VIEW_MODE_SUBSCRIPTION_MS), WearViewMode.LIST)

    /**
     * S2486: the screen's own half of the gate. The entry point is already hidden where this is false,
     * but both routes to this screen stay registered for back-stack entries an older build saved, and
     * de-registering them would trade a review finding for a navigation crash.
     */
    val offersCredentialEntry: Boolean = restrictedCapabilities.offersCredentialEntry

    private val _uiState = MutableStateFlow(AddNetworkSourceUiState())
    val uiState: StateFlow<AddNetworkSourceUiState> = _uiState.asStateFlow()

    fun setProtocol(protocol: NetworkSourceType) {
        _uiState.value = _uiState.value.copy(
            protocol = protocol,
            port = defaultPort(protocol),
            statusMessage = "",
            isError = false,
            useSshKey = if (protocol == NetworkSourceType.SFTP) _uiState.value.useSshKey else false,
            sshPrivateKey = if (protocol == NetworkSourceType.SFTP) _uiState.value.sshPrivateKey else ""
        )
    }

    fun setName(name: String) {
        _uiState.value = _uiState.value.copy(name = name, statusMessage = "", isError = false)
    }

    fun setServer(server: String) {
        _uiState.value = _uiState.value.copy(server = server, statusMessage = "", isError = false)
    }

    fun setPort(port: String) {
        val parsedPort = port.toIntOrNull() ?: 0
        _uiState.value = _uiState.value.copy(port = parsedPort, statusMessage = "", isError = false)
    }

    fun setUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username, statusMessage = "", isError = false)
    }

    fun setPassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, statusMessage = "", isError = false)
    }

    fun setShareName(shareName: String) {
        _uiState.value = _uiState.value.copy(shareName = shareName, statusMessage = "", isError = false)
    }

    fun setDomain(domain: String) {
        _uiState.value = _uiState.value.copy(domain = domain, statusMessage = "", isError = false)
    }

    fun setBasePath(basePath: String) {
        _uiState.value = _uiState.value.copy(basePath = basePath, statusMessage = "", isError = false)
    }

    fun setUseSshKey(useSshKey: Boolean) {
        _uiState.value = _uiState.value.copy(useSshKey = useSshKey, statusMessage = "", isError = false)
    }

    fun setSshPrivateKey(sshPrivateKey: String) {
        _uiState.value = _uiState.value.copy(sshPrivateKey = sshPrivateKey, statusMessage = "", isError = false)
    }

    fun testConnection() {
        val currentState = _uiState.value
        val validationError = validate(currentState, requireShareName = currentState.protocol == NetworkSourceType.SMB)
        if (validationError != null) {
            _uiState.value = currentState.copy(statusMessage = validationError, isError = true)
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(
                isLoading = true,
                statusMessage = context.getString(R.string.testing_connection),
                isError = false
            )

            val result = networkSourceRepository.testConnection(buildSource(currentState))
            _uiState.value = if (result.isSuccess && result.getOrDefault(false)) {
                currentState.copy(
                    isLoading = false,
                    statusMessage = context.getString(R.string.connection_successful),
                    isError = false
                )
            } else {
                val failure = result.exceptionOrNull()
                val message = if (failure is UnsupportedOperationException) {
                    context.getString(R.string.connection_test_not_supported)
                } else {
                    context.getString(
                        R.string.connection_failed_with_reason,
                        failure?.message ?: context.getString(R.string.unknown_error)
                    )
                }
                currentState.copy(
                    isLoading = false,
                    statusMessage = message,
                    isError = true
                )
            }
        }
    }

    fun saveSource(onSuccess: () -> Unit = {}) {
        val currentState = _uiState.value
        val validationError = validate(currentState, requireShareName = currentState.protocol == NetworkSourceType.SMB)
        if (validationError != null) {
            _uiState.value = currentState.copy(statusMessage = validationError, isError = true)
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(
                isLoading = true,
                statusMessage = context.getString(R.string.saving_connection),
                isError = false
            )

            try {
                val source = buildSource(currentState)
                networkSourceRepository.addSource(source)
                Timber.d("Saved network source: ${source.name} (${source.type})")
                _uiState.value = currentState.copy(
                    isLoading = false,
                    statusMessage = context.getString(R.string.connection_saved),
                    isError = false
                )
                onSuccess()
            } catch (e: Exception) {
                e.errorUnlessCancellation("Failed to save network source")
                _uiState.value = currentState.copy(
                    isLoading = false,
                    statusMessage = context.getString(
                        R.string.failed_to_save_with_reason,
                        e.message ?: context.getString(R.string.unknown_error)
                    ),
                    isError = true
                )
            }
        }
    }

    private fun validate(state: AddNetworkSourceUiState, requireShareName: Boolean): String? {
        if (state.server.isBlank()) {
            return context.getString(R.string.server_required)
        }
        if (state.port <= 0) {
            return context.getString(R.string.port_required)
        }
        if (requireShareName && state.shareName.isBlank()) {
            return context.getString(R.string.server_and_share_required)
        }
        if (state.protocol == NetworkSourceType.SFTP && state.useSshKey && state.sshPrivateKey.isBlank()) {
            return context.getString(R.string.ssh_key_required)
        }
        return null
    }

    private fun buildSource(state: AddNetworkSourceUiState): NetworkSource {
        val derivedName = when {
            state.name.isNotBlank() -> state.name
            state.protocol == NetworkSourceType.SMB && state.shareName.isNotBlank() -> "${state.server}/${state.shareName}"
            else -> state.server
        }
        return NetworkSource(
            type = state.protocol,
            name = derivedName,
            server = state.server,
            port = state.port,
            username = state.username.ifBlank { "guest" },
            password = state.password,
            shareName = state.shareName.ifBlank { null },
            domain = state.domain,
            sshPrivateKey = state.sshPrivateKey.ifBlank { null },
            // S1833: the form produces this now. It used to be a literal root, which every
            // server accepts, so the path branch of the connection test was unreachable.
            basePath = state.basePath.ifBlank { "/" }
        )
    }

    private fun defaultPort(protocol: NetworkSourceType): Int {
        return when (protocol) {
            NetworkSourceType.SMB -> 445
            NetworkSourceType.FTP -> 21
            NetworkSourceType.SFTP -> 22
            NetworkSourceType.GOOGLE_DRIVE -> 443
        }
    }
}
