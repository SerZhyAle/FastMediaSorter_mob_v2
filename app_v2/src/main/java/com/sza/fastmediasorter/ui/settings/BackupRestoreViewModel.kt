package com.sza.fastmediasorter.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveCredentialsManager
import com.sza.fastmediasorter.domain.usecase.BackupToGoogleDriveUseCase
import com.sza.fastmediasorter.domain.usecase.RestoreFromGoogleDriveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class BackupRestoreUiState {
    object Idle : BackupRestoreUiState()
    object Authenticating : BackupRestoreUiState()
    object NeedsSignIn : BackupRestoreUiState()
    object BackingUp : BackupRestoreUiState()
    object Restoring : BackupRestoreUiState()
    object FetchingInfo : BackupRestoreUiState()
    data class BackupSuccess(val resourceCount: Int, val accountEmail: String) : BackupRestoreUiState()
    data class RestoreSuccess(val result: RestoreFromGoogleDriveUseCase.RestoreResult) : BackupRestoreUiState()
    data class BackupInfoReady(val info: RestoreFromGoogleDriveUseCase.BackupInfo) : BackupRestoreUiState()
    data class Error(val message: String) : BackupRestoreUiState()
}

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    private val backupUseCase: BackupToGoogleDriveUseCase,
    private val restoreUseCase: RestoreFromGoogleDriveUseCase,
    private val googleDriveClient: GoogleDriveRestClient,
    private val credentialsManager: GoogleDriveCredentialsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupRestoreUiState>(BackupRestoreUiState.Idle)
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    /** Pending action after auth completes: "backup" or "restore" */
    private var pendingAction: String? = null

    fun getSignInIntent(): Intent = googleDriveClient.getSignInIntent()

    fun isAuthenticated(): Boolean = googleDriveClient.isAuthenticated()

    fun getAccountEmail(): String? = googleDriveClient.getAccountEmail()

    /**
     * Called when user taps Backup button.
     * If authenticated, backs up immediately. Otherwise, triggers sign-in.
     */
    fun startBackup() {
        viewModelScope.launch {
            if (googleDriveClient.isAuthenticated()) {
                performBackup()
            } else {
                // Try silent auth first
                _uiState.value = BackupRestoreUiState.Authenticating
                val restored = googleDriveClient.tryRestoreFromStorage()
                if (restored) {
                    performBackup()
                } else {
                    pendingAction = "backup"
                    _uiState.value = BackupRestoreUiState.NeedsSignIn
                }
            }
        }
    }

    /**
     * Called when user taps Restore button.
     * Fetches backup info first for confirmation dialog.
     */
    fun startRestore() {
        viewModelScope.launch {
            if (googleDriveClient.isAuthenticated()) {
                fetchBackupInfo()
            } else {
                _uiState.value = BackupRestoreUiState.Authenticating
                val restored = googleDriveClient.tryRestoreFromStorage()
                if (restored) {
                    fetchBackupInfo()
                } else {
                    pendingAction = "restore"
                    _uiState.value = BackupRestoreUiState.NeedsSignIn
                }
            }
        }
    }

    /** After user confirms restore in dialog */
    fun confirmRestore() {
        viewModelScope.launch {
            performRestore()
        }
    }

    /**
     * Process sign-in result from Activity.
     */
    fun handleSignInResult(account: GoogleSignInAccount?) {
        viewModelScope.launch {
            if (account == null) {
                _uiState.value = BackupRestoreUiState.Error("Google sign-in was cancelled")
                pendingAction = null
                return@launch
            }

            val authResult = googleDriveClient.handleSignInResult(account)
            when (authResult) {
                is com.sza.fastmediasorter.data.cloud.AuthResult.Success -> {
                    when (pendingAction) {
                        "backup" -> performBackup()
                        "restore" -> fetchBackupInfo()
                    }
                    pendingAction = null
                }
                is com.sza.fastmediasorter.data.cloud.AuthResult.Error -> {
                    _uiState.value = BackupRestoreUiState.Error("Authentication failed: ${authResult.message}")
                    pendingAction = null
                }
                is com.sza.fastmediasorter.data.cloud.AuthResult.Cancelled -> {
                    _uiState.value = BackupRestoreUiState.Error("Authentication cancelled")
                    pendingAction = null
                }
            }
        }
    }

    /** Whether we need the caller to launch sign-in intent */
    fun needsSignIn(): Boolean = pendingAction != null && !googleDriveClient.isAuthenticated()

    fun resetState() {
        _uiState.value = BackupRestoreUiState.Idle
    }

    private suspend fun performBackup() {
        _uiState.value = BackupRestoreUiState.BackingUp
        val result = backupUseCase()
        result.onSuccess { backupResult ->
            _uiState.value = BackupRestoreUiState.BackupSuccess(
                backupResult.resourceCount,
                backupResult.accountEmail
            )
        }.onFailure { error ->
            Timber.e(error, "Backup failed")
            _uiState.value = BackupRestoreUiState.Error(error.message ?: "Backup failed")
        }
    }

    private suspend fun fetchBackupInfo() {
        _uiState.value = BackupRestoreUiState.FetchingInfo
        val result = restoreUseCase.getBackupInfo()
        result.onSuccess { info ->
            _uiState.value = BackupRestoreUiState.BackupInfoReady(info)
        }.onFailure { error ->
            Timber.e(error, "Failed to fetch backup info")
            _uiState.value = BackupRestoreUiState.Error(error.message ?: "No backup found")
        }
    }

    private suspend fun performRestore() {
        _uiState.value = BackupRestoreUiState.Restoring
        val result = restoreUseCase()
        result.onSuccess { restoreResult ->
            _uiState.value = BackupRestoreUiState.RestoreSuccess(restoreResult)
        }.onFailure { error ->
            Timber.e(error, "Restore failed")
            _uiState.value = BackupRestoreUiState.Error(error.message ?: "Restore failed")
        }
    }
}
