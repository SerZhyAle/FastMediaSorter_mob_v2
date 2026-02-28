package com.sza.fastmediasorter.ui.resourceeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ResourceConnectionTestResult
import com.sza.fastmediasorter.domain.model.ResourceEditorMode
import com.sza.fastmediasorter.domain.model.ResourceErrorCode
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ResourceValidationResult
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.applyProfile
import com.sza.fastmediasorter.domain.strategy.ResourceFieldSchema
import com.sza.fastmediasorter.domain.usecase.ResourceEditorSaveResult
import com.sza.fastmediasorter.domain.usecase.ResourceEditorUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

data class ResourceFieldState(
    val errorCode: ResourceErrorCode? = null,
    val isDirty: Boolean = false
)

enum class ResourceEditorWarning {
    READ_ONLY_DESTINATION,
    ENDPOINT_CHANGED_RESCAN,
    PATH_DUPLICATE_EXISTING
}

data class ResourceEditorUiState(
    val formData: ResourceFormData = ResourceFormData(),
    val originalSnapshot: ResourceFormData? = null,
    val fieldSchema: List<ResourceFieldSchema> = emptyList(),
    val fieldStates: Map<ResourceFieldKey, ResourceFieldState> = emptyMap(),
    val isTestingConnection: Boolean = false,
    val isSaving: Boolean = false,
    val hasChanges: Boolean = false,
    val isFormValid: Boolean = false,
    val canSave: Boolean = false,
    val showSaveAsCopy: Boolean = false,
    val hasNameCollision: Boolean = false,
    val nameSuggestions: List<String> = emptyList(),
    val requiresCredentialChoice: Boolean = false,
    val warnings: Set<ResourceEditorWarning> = emptySet(),
    val connectionResult: ResourceConnectionTestResult? = null,
    val saveResult: ResourceEditorSaveResult? = null,
    val isReadOnlyMode: Boolean = false,
    val statistics: ResourceStatistics? = null
)

data class ResourceStatistics(
    val fileCount: Int = 0,
    val subfolderCount: Int = 0,
    val createdDate: Long? = null,
    val lastBrowseDate: Long? = null,
    val lastSyncDate: Long? = null,
    val readSpeedMbps: Double? = null,
    val writeSpeedMbps: Double? = null
)

sealed interface ResourceEditorUiEvent {
    data class ShowError(
        val message: String? = null,
        val messageResId: Int? = null
    ) : ResourceEditorUiEvent

    data class ShowInfo(
        val message: String? = null,
        val messageResId: Int? = null
    ) : ResourceEditorUiEvent

    data class Saved(val resourceId: Long) : ResourceEditorUiEvent
}

@HiltViewModel
class ResourceFormViewModel @Inject constructor(
    private val resourceEditorUseCase: ResourceEditorUseCase
) : ViewModel() {

    private enum class LastAction {
        NONE,
        TEST_CONNECTION,
        SAVE
    }

    private val _uiState = MutableStateFlow(ResourceEditorUiState())
    val uiState: StateFlow<ResourceEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ResourceEditorUiEvent>()
    val events: SharedFlow<ResourceEditorUiEvent> = _events.asSharedFlow()

    private var lastAction: LastAction = LastAction.NONE
    private var existingResourceNames: Set<String> = emptySet()
    private var existingPathKeys: Set<Pair<ResourceType, String>> = emptySet()

    fun initialize(
        mode: ResourceEditorMode,
        resourceType: ResourceType = ResourceType.LOCAL,
        resourceId: Long? = null
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                resourceEditorUseCase.initialize(mode, resourceType, resourceId)
            }

            result.onSuccess { formData ->
                existingResourceNames = withContext(Dispatchers.IO) {
                    resourceEditorUseCase.getExistingResourceNames(formData.id)
                }
                val currentNameNormalized = formData.name.trim()
                if (currentNameNormalized.isNotEmpty()) {
                    existingResourceNames = existingResourceNames
                        .filterNot { it.trim().equals(currentNameNormalized, ignoreCase = true) }
                        .toSet()
                }
                existingPathKeys = withContext(Dispatchers.IO) {
                    resourceEditorUseCase.getExistingPathKeys(formData.id)
                }

                val preparedFormData = if (formData.mode == ResourceEditorMode.COPY) {
                    formData.copy(
                        name = resourceEditorUseCase.generateUniqueCopyName(
                            sourceName = formData.name,
                            existingNames = existingResourceNames
                        )
                    )
                } else {
                    formData
                }

                val initialized = _uiState.value.copy(
                        formData = preparedFormData,
                        originalSnapshot = preparedFormData,
                        fieldSchema = resourceEditorUseCase.fieldSchema(preparedFormData.type),
                        fieldStates = emptyMap(),
                        connectionResult = null,
                        saveResult = null,
                        isReadOnlyMode = preparedFormData.isReadOnly,
                        requiresCredentialChoice = preparedFormData.mode == ResourceEditorMode.COPY &&
                            (preparedFormData.credentialsId != null ||
                                preparedFormData.username.isNotBlank() ||
                                preparedFormData.password.isNotBlank()),
                        statistics = if (mode == ResourceEditorMode.EDIT && resourceId != null) {
                            withContext(Dispatchers.IO) {
                                resourceEditorUseCase.getResourceStatistics(resourceId)
                            }
                        } else null
                    )
                _uiState.value = recalculateState(initialized)
            }.onFailure { error ->
                Timber.e(error, "ResourceFormViewModel: initialize failed")
                _events.emit(
                    ResourceEditorUiEvent.ShowError(
                        messageResId = R.string.resource_editor_init_failed
                    )
                )
            }
        }
    }

    fun onFieldChanged(fieldKey: ResourceFieldKey, value: Any?) {
        _uiState.update { current ->
            val updatedForm = when (fieldKey) {
                ResourceFieldKey.NAME -> current.formData.copy(name = value as? String ?: "")
                ResourceFieldKey.PATH -> current.formData.copy(path = value as? String ?: "")
                ResourceFieldKey.TYPE -> current.formData.copy(type = value as? ResourceType ?: current.formData.type)
                ResourceFieldKey.HOST -> {
                    val newHost = value as? String ?: ""
                    val updated = current.formData.copy(host = newHost)
                    // Auto-fill name from host if name is still empty (CREATE mode)
                    if (current.formData.id == null && current.formData.name.isBlank() && newHost.isNotBlank()) {
                        updated.copy(name = newHost)
                    } else {
                        updated
                    }
                }
                ResourceFieldKey.PORT -> current.formData.copy(port = (value as? String)?.toIntOrNull() ?: (value as? Int))
                ResourceFieldKey.USERNAME -> current.formData.copy(username = value as? String ?: "")
                ResourceFieldKey.PASSWORD -> current.formData.copy(password = value as? String ?: "")
                ResourceFieldKey.ACCESS_PIN -> current.formData.copy(accessPin = value as? String ?: "")
                ResourceFieldKey.COMMENT -> current.formData.copy(comment = value as? String ?: "")
                ResourceFieldKey.CLOUD_PROVIDER -> current.formData.copy(cloudProvider = value as? com.sza.fastmediasorter.data.cloud.CloudProvider)
                ResourceFieldKey.CLOUD_FOLDER -> current.formData.copy(cloudFolderId = value as? String)
                ResourceFieldKey.MEDIA_TYPES -> current.formData.copy(
                    supportedMediaTypes = extractMediaTypes(value, current.formData.supportedMediaTypes),
                    profile = ResourceProfile.NONE // Manual change clears profile preset
                )
                ResourceFieldKey.SLIDESHOW_INTERVAL -> current.formData.copy(
                    slideshowInterval = (value as? String)?.toIntOrNull() ?: (value as? Int) ?: 10
                )
                ResourceFieldKey.IS_DESTINATION -> current.formData.copy(
                    isDestination = value as? Boolean ?: false
                )
                ResourceFieldKey.DESTINATION_COLOR -> current.formData.copy(
                    destinationColor = (value as? Int) ?: current.formData.destinationColor
                )
                ResourceFieldKey.IS_READ_ONLY -> current.formData.copy(
                    isReadOnly = value as? Boolean ?: false
                )
                ResourceFieldKey.SCAN_SUBDIRECTORIES -> current.formData.copy(
                    scanSubdirectories = value as? Boolean ?: false
                )
                ResourceFieldKey.ALL_FILES -> current.formData.copy(
                    allFiles = value as? Boolean ?: false
                )
                ResourceFieldKey.DISABLE_THUMBNAILS -> current.formData.copy(
                    disableThumbnails = value as? Boolean ?: false
                )
                ResourceFieldKey.SHOW_HIDDEN_FILES -> current.formData.copy(
                    showHiddenFiles = value as? Boolean ?: false
                )
                ResourceFieldKey.SHOW_SUBFOLDERS_AS_ITEMS -> current.formData.copy(
                    showSubfoldersAsItems = value as? Boolean ?: false
                )
                ResourceFieldKey.REMEMBER_FILE_LIST -> current.formData.copy(
                    rememberFileList = value as? Boolean ?: false
                )
                ResourceFieldKey.SHOW_COMMAND_PANEL -> current.formData.copy(
                    showCommandPanel = value as? Boolean
                )
                else -> current.formData
            }

            recalculateState(
                current.copy(
                formData = updatedForm,
                fieldSchema = resourceEditorUseCase.fieldSchema(updatedForm.type),
                fieldStates = current.fieldStates.toMutableMap().apply {
                    put(fieldKey, ResourceFieldState(isDirty = true))
                }
            )
            )
        }

        applyValidation()
    }

    fun onUseNameSuggestion(suggestedName: String) {
        onFieldChanged(ResourceFieldKey.NAME, suggestedName)
    }

    /**
     * Applies a quick-setup [ResourceProfile] preset to the form data,
     * overwriting relevant fields (supportedMediaTypes, allFiles, rememberFileList, etc.).
     * [ResourceProfile.NONE] is a no-op.
     */
    fun onProfileSelected(profile: ResourceProfile) {
        if (profile == ResourceProfile.NONE) return
        _uiState.update { current ->
            val updated = current.formData.applyProfile(profile)
            recalculateState(
                current.copy(
                    formData = updated,
                    fieldSchema = resourceEditorUseCase.fieldSchema(updated.type)
                )
            )
        }
        applyValidation()
    }

    fun onCredentialBehaviorSelected(keepCredentials: Boolean) {
        _uiState.update {
            val updatedForm = if (keepCredentials) {
                it.formData
            } else {
                it.formData.copy(
                    credentialsId = null,
                    username = "",
                    password = ""
                )
            }
            recalculateState(
                it.copy(
                    formData = updatedForm,
                    requiresCredentialChoice = false
                )
            )
        }
    }

    fun onTestConnection() {
        val currentForm = _uiState.value.formData
        lastAction = LastAction.TEST_CONNECTION

        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, connectionResult = null) }
            val validation = resourceEditorUseCase.validate(currentForm)
            if (!validation.isValid) {
                applyValidation(validation)
                _uiState.update { it.copy(isTestingConnection = false) }
                _events.emit(ResourceEditorUiEvent.ShowError(messageResId = R.string.resource_editor_validation_before_test))
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                resourceEditorUseCase.testConnection(currentForm)
            }

            _uiState.update {
                it.copy(
                    isTestingConnection = false,
                    connectionResult = result
                )
            }
        }
    }

    fun onSave() {
        val state = _uiState.value
        val currentForm = state.formData
        if (!state.canSave) {
            viewModelScope.launch {
                _events.emit(ResourceEditorUiEvent.ShowInfo(messageResId = R.string.resource_editor_no_changes_or_invalid))
            }
            return
        }
        lastAction = LastAction.SAVE

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveResult = null) }

            val validation = resourceEditorUseCase.validate(currentForm)
            if (!validation.isValid) {
                applyValidation(validation)
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(ResourceEditorUiEvent.ShowError(messageResId = R.string.resource_editor_validation_failed))
                return@launch
            }

            val saveResult = withContext(Dispatchers.IO) {
                resourceEditorUseCase.save(currentForm)
            }

            saveResult.onSuccess { result ->
                _uiState.update {
                    recalculateState(
                        it.copy(
                        isSaving = false,
                        saveResult = result,
                        originalSnapshot = currentForm,
                        fieldStates = emptyMap()
                    )
                    )
                }
                _events.emit(ResourceEditorUiEvent.Saved(result.resourceId))
            }.onFailure { error ->
                Timber.e(error, "ResourceFormViewModel: save failed")
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(
                    ResourceEditorUiEvent.ShowError(
                        messageResId = R.string.error_save_failed
                    )
                )
            }
        }
    }

    fun onSaveAsCopy() {
        val state = _uiState.value
        if (state.formData.mode != ResourceEditorMode.EDIT) {
            viewModelScope.launch {
                _events.emit(ResourceEditorUiEvent.ShowInfo(messageResId = R.string.resource_editor_save_as_copy_edit_only))
            }
            return
        }

        val copyName = if (state.formData.name.contains("(Copy)")) {
            state.formData.name
        } else {
            "${state.formData.name} (Copy)"
        }

        _uiState.update {
            recalculateState(
                it.copy(
                    formData = it.formData.copy(
                        id = null,
                        mode = ResourceEditorMode.COPY,
                        name = copyName
                    )
                )
            )
        }
        onSave()
    }

    fun onResetChanges() {
        val snapshot = _uiState.value.originalSnapshot ?: return
        _uiState.update {
            recalculateState(
                it.copy(
                    formData = snapshot,
                    fieldSchema = resourceEditorUseCase.fieldSchema(snapshot.type),
                    fieldStates = emptyMap(),
                    connectionResult = null
                )
            )
        }
    }

    fun onRetry() {
        when (lastAction) {
            LastAction.TEST_CONNECTION -> onTestConnection()
            LastAction.SAVE -> onSave()
            LastAction.NONE -> {
                viewModelScope.launch {
                    _events.emit(ResourceEditorUiEvent.ShowInfo(messageResId = R.string.resource_editor_nothing_to_retry))
                }
            }
        }
    }

    private fun applyValidation(validation: ResourceValidationResult = resourceEditorUseCase.validate(_uiState.value.formData)) {
        _uiState.update { current ->
            val mergedStates = current.fieldStates.toMutableMap()

            validation.fieldErrors.forEach { (key, errorCode) ->
                val existing = mergedStates[key] ?: ResourceFieldState()
                mergedStates[key] = existing.copy(errorCode = errorCode)
            }

            val updated = if (validation.isValid) {
                current.copy(fieldStates = mergedStates.mapValues { (_, state) -> state.copy(errorCode = null) })
            } else {
                current.copy(fieldStates = mergedStates)
            }
            recalculateState(updated, validation)
        }
    }

    private fun recalculateState(
        state: ResourceEditorUiState,
        validation: ResourceValidationResult? = null
    ): ResourceEditorUiState {
        val resolvedValidation = validation ?: resourceEditorUseCase.validate(state.formData)
        val hasChanges = state.originalSnapshot?.let { it != state.formData } ?: false
        val warnings = buildWarnings(state.formData, state.originalSnapshot)
        val normalizedName = state.formData.name.trim()
        val originalName = state.originalSnapshot?.name?.trim()
        // The initial snapshot name is already accepted for this form session and must never
        // be treated as a duplicate of itself (mode-agnostic, case-insensitive).
        val isOriginalNameUnchanged = !originalName.isNullOrEmpty() &&
            normalizedName.equals(originalName, ignoreCase = true)
        val hasNameCollision = normalizedName.isNotBlank() &&
            existingResourceNames.any { it.trim().equals(normalizedName, ignoreCase = true) } &&
            !isOriginalNameUnchanged

        val suggestions = if (hasNameCollision) {
            resourceEditorUseCase.buildNameSuggestions(normalizedName, existingResourceNames)
        } else {
            emptyList()
        }

        return state.copy(
            hasChanges = hasChanges,
            isFormValid = resolvedValidation.isValid,
            canSave = resolvedValidation.isValid && hasChanges && !hasNameCollision && !state.isSaving && !state.isTestingConnection,
            showSaveAsCopy = state.formData.mode == ResourceEditorMode.EDIT,
            hasNameCollision = hasNameCollision,
            nameSuggestions = suggestions,
            warnings = warnings
        )
    }

    private fun buildWarnings(
        currentForm: ResourceFormData,
        snapshot: ResourceFormData?
    ): Set<ResourceEditorWarning> {
        val warnings = mutableSetOf<ResourceEditorWarning>()

        if (currentForm.isDestination && currentForm.isReadOnly) {
            warnings.add(ResourceEditorWarning.READ_ONLY_DESTINATION)
        }

        if (currentForm.mode == ResourceEditorMode.EDIT && snapshot != null) {
            val pathChanged = currentForm.path != snapshot.path
            val endpointChanged = currentForm.host != snapshot.host || currentForm.port != snapshot.port
            if (pathChanged || endpointChanged) {
                warnings.add(ResourceEditorWarning.ENDPOINT_CHANGED_RESCAN)
            }
        }

        if (currentForm.mode == ResourceEditorMode.COPY) {
            val key = currentForm.type to currentForm.path.trim()
            if (key.second.isNotBlank() && existingPathKeys.contains(key)) {
                warnings.add(ResourceEditorWarning.PATH_DUPLICATE_EXISTING)
            }
        }

        return warnings
    }

    private fun extractMediaTypes(value: Any?, fallback: Set<MediaType>): Set<MediaType> {
        val rawSet = value as? Set<*> ?: return fallback
        val typed = rawSet.filterIsInstance<MediaType>().toSet()
        return if (typed.isEmpty()) fallback else typed
    }
}