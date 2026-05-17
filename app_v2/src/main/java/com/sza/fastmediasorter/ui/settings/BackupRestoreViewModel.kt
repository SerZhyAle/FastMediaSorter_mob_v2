package com.sza.fastmediasorter.ui.settings

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.cloud.GoogleDriveAuthPlugin
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveCredentialsManager
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.IdentityFailureReason
import com.sza.fastmediasorter.domain.identity.IdentitySignInResult
import com.sza.fastmediasorter.domain.model.FavoritesConflictStrategy
import com.sza.fastmediasorter.domain.model.FavoritesExportResult
import com.sza.fastmediasorter.domain.model.FavoritesImportPreview
import com.sza.fastmediasorter.domain.model.FavoritesImportResult
import com.sza.fastmediasorter.domain.usecase.BackupToGoogleDriveUseCase
import com.sza.fastmediasorter.domain.usecase.ExportFavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.ImportFavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.RestoreFromGoogleDriveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ── Favorites export/import UI state ─────────────────────────────────────────

sealed class FavoritesExportUiState {
    object Idle : FavoritesExportUiState()
    object Loading : FavoritesExportUiState()
    data class Success(val result: FavoritesExportResult) : FavoritesExportUiState()
    data class Error(val message: String) : FavoritesExportUiState()
}

sealed class FavoritesImportUiState {
    object Idle : FavoritesImportUiState()
    object LoadingPreview : FavoritesImportUiState()
    data class Preview(val preview: FavoritesImportPreview, val uri: Uri) : FavoritesImportUiState()
    object Importing : FavoritesImportUiState()
    data class Success(val result: FavoritesImportResult) : FavoritesImportUiState()
    data class Error(val message: String) : FavoritesImportUiState()
}

// ── Google Drive backup/restore UI state ─────────────────────────────────────

sealed class BackupRestoreUiState {
    object Idle : BackupRestoreUiState()
    object Authenticating : BackupRestoreUiState()
    object NeedsSignIn : BackupRestoreUiState()
    object BackingUp : BackupRestoreUiState()
    object Restoring : BackupRestoreUiState()
    object FetchingInfo : BackupRestoreUiState()
    data class BackupSuccess(val resourceCount: Int, val favoritesCount: Int, val accountEmail: String) : BackupRestoreUiState()
    data class RestoreSuccess(val result: RestoreFromGoogleDriveUseCase.RestoreResult) : BackupRestoreUiState()
    data class BackupInfoReady(val info: RestoreFromGoogleDriveUseCase.BackupInfo) : BackupRestoreUiState()
    data class Error(val message: String) : BackupRestoreUiState()
}

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backupUseCase: BackupToGoogleDriveUseCase,
    private val restoreUseCase: RestoreFromGoogleDriveUseCase,
    private val googleDriveClient: GoogleDriveRestClient,
    private val credentialsManager: GoogleDriveCredentialsManager,
    private val exportFavoritesUseCase: ExportFavoritesUseCase,
    private val importFavoritesUseCase: ImportFavoritesUseCase,
    // S0200 Phase 04b — direct identity-domain access for the new sign-in path.
    private val identityRepository: GoogleIdentityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupRestoreUiState>(BackupRestoreUiState.Idle)
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    private val _exportFavState = MutableStateFlow<FavoritesExportUiState>(FavoritesExportUiState.Idle)
    val exportFavState: StateFlow<FavoritesExportUiState> = _exportFavState.asStateFlow()

    private val _importFavState = MutableStateFlow<FavoritesImportUiState>(FavoritesImportUiState.Idle)
    val importFavState: StateFlow<FavoritesImportUiState> = _importFavState.asStateFlow()

    /** Pending action after auth completes: "backup" or "restore" */
    private var pendingAction: String? = null

    /**
     * S0200 Phase 04b: launch Credential Manager sign-in via the identity domain.
     * On success, continues any [pendingAction] (backup / restore). On cancellation or failure,
     * surfaces a user-facing error via [_uiState].
     */
    fun startSignIn(activity: Activity) {
        viewModelScope.launch {
            _uiState.value = BackupRestoreUiState.Authenticating
            val result = identityRepository.signInPrimary(activity, GoogleDriveAuthPlugin.DRIVE_SIGN_IN_SCOPES)
            when (result) {
                is IdentitySignInResult.Success -> {
                    when (pendingAction) {
                        "backup" -> performBackup()
                        "restore" -> fetchBackupInfo()
                        else -> _uiState.value = BackupRestoreUiState.Idle
                    }
                    pendingAction = null
                }
                IdentitySignInResult.Cancelled -> {
                    _uiState.value = BackupRestoreUiState.Error(
                        context.getString(R.string.google_sign_in_cancelled)
                    )
                    pendingAction = null
                }
                is IdentitySignInResult.Failed -> {
                    val message = when (result.reason) {
                        IdentityFailureReason.UserCancelled ->
                            context.getString(R.string.google_sign_in_cancelled)
                        IdentityFailureReason.NetworkError ->
                            context.getString(R.string.no_internet_connection)
                        else ->
                            context.getString(R.string.google_drive_authentication_failed)
                    }
                    _uiState.value = BackupRestoreUiState.Error(message)
                    pendingAction = null
                }
            }
        }
    }

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

    /** Whether we need the caller to launch sign-in intent */
    fun needsSignIn(): Boolean = pendingAction != null && !googleDriveClient.isAuthenticated()

    fun resetState() {
        _uiState.value = BackupRestoreUiState.Idle
    }

    private fun getRestoreFailureMessage(errorMessage: String?): String {
        // Keep the missing-backup guidance specific without surfacing raw backend text.
        return if (
            errorMessage?.contains("no backup", ignoreCase = true) == true ||
            errorMessage?.contains("not found", ignoreCase = true) == true
        ) {
            context.getString(R.string.restore_no_backup)
        } else {
            context.getString(R.string.restore_failed)
        }
    }

    private suspend fun performBackup() {
        _uiState.value = BackupRestoreUiState.BackingUp
        val result = backupUseCase()
        result.onSuccess { backupResult ->
            _uiState.value = BackupRestoreUiState.BackupSuccess(
                backupResult.resourceCount,
                backupResult.favoritesCount,
                backupResult.accountEmail
            )
        }.onFailure { error ->
            Timber.e(error, "Backup failed")
            _uiState.value = BackupRestoreUiState.Error(
                context.getString(R.string.backup_failed)
            )
        }
    }

    private suspend fun fetchBackupInfo() {
        _uiState.value = BackupRestoreUiState.FetchingInfo
        val result = restoreUseCase.getBackupInfo()
        result.onSuccess { info ->
            _uiState.value = BackupRestoreUiState.BackupInfoReady(info)
        }.onFailure { error ->
            Timber.e(error, "Failed to fetch backup info")
            _uiState.value = BackupRestoreUiState.Error(
                getRestoreFailureMessage(error.message)
            )
        }
    }

    private suspend fun performRestore() {
        _uiState.value = BackupRestoreUiState.Restoring
        val result = restoreUseCase()
        result.onSuccess { restoreResult ->
            _uiState.value = BackupRestoreUiState.RestoreSuccess(restoreResult)
        }.onFailure { error ->
            Timber.e(error, "Restore failed")
            _uiState.value = BackupRestoreUiState.Error(
                context.getString(R.string.restore_failed)
            )
        }
    }

    // ── Favorites Export ──────────────────────────────────────────────────────

    fun exportFavorites() {
        viewModelScope.launch {
            _exportFavState.value = FavoritesExportUiState.Loading
            val result = exportFavoritesUseCase()
            _exportFavState.value = if (result.isSuccess) {
                FavoritesExportUiState.Success(result)
            } else {
                FavoritesExportUiState.Error(
                    context.getString(R.string.export_fav_failed)
                )
            }
        }
    }

    fun resetExportFavState() {
        _exportFavState.value = FavoritesExportUiState.Idle
    }

    // ── Favorites Import ──────────────────────────────────────────────────────

    fun previewFavoritesImport(uri: Uri) {
        viewModelScope.launch {
            _importFavState.value = FavoritesImportUiState.LoadingPreview
            importFavoritesUseCase.preview(uri)
                .onSuccess { preview ->
                    _importFavState.value = FavoritesImportUiState.Preview(preview, uri)
                }
                .onFailure { error ->
                    Timber.e(error, "Import preview failed")
                    _importFavState.value = FavoritesImportUiState.Error(
                        context.getString(R.string.import_fav_invalid_file)
                    )
                }
        }
    }

    fun confirmFavoritesImport(uri: Uri, strategy: FavoritesConflictStrategy) {
        viewModelScope.launch {
            _importFavState.value = FavoritesImportUiState.Importing
            val result = importFavoritesUseCase(uri, strategy)
            _importFavState.value = if (result.isSuccess) {
                FavoritesImportUiState.Success(result)
            } else {
                FavoritesImportUiState.Error(
                    context.getString(R.string.import_fav_failed)
                )
            }
        }
    }

    fun resetImportFavState() {
        _importFavState.value = FavoritesImportUiState.Idle
    }
}
