package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.util.DestinationColors
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceConnectionStatus
import com.sza.fastmediasorter.domain.model.ResourceConnectionTestResult
import com.sza.fastmediasorter.domain.model.ResourceEditorMode
import com.sza.fastmediasorter.domain.model.ResourceErrorCode
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ResourceValidationResult
import com.sza.fastmediasorter.domain.model.ResourceVerificationStatus
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.strategy.CloudResourceStrategy
import com.sza.fastmediasorter.domain.strategy.FtpResourceStrategy
import com.sza.fastmediasorter.domain.strategy.LocalResourceStrategy
import com.sza.fastmediasorter.domain.strategy.ResourceFieldSchema
import com.sza.fastmediasorter.domain.strategy.ResourceStrategy
import com.sza.fastmediasorter.domain.strategy.SftpResourceStrategy
import com.sza.fastmediasorter.domain.strategy.SmbResourceStrategy
import com.sza.fastmediasorter.utils.FtpPathUtils
import com.sza.fastmediasorter.utils.SftpPathUtils
import com.sza.fastmediasorter.utils.SmbPathUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

data class ResourceEditorSaveResult(
    val resourceId: Long,
    val verificationStatus: ResourceVerificationStatus
)

class ResourceEditorUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val addResourceUseCase: AddResourceUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _verificationStatuses = MutableStateFlow<Map<Long, ResourceVerificationStatus>>(emptyMap())
    val verificationStatuses: StateFlow<Map<Long, ResourceVerificationStatus>> = _verificationStatuses.asStateFlow()

    private val strategies: Map<ResourceType, ResourceStrategy> = mapOf(
        ResourceType.LOCAL to LocalResourceStrategy(),
        ResourceType.SMB to SmbResourceStrategy { form ->
            val normalized = normalizeForStrategy(form)
            val result = smbOperationsUseCase.testConnection(
                server = normalized.host,
                shareName = normalized.path,
                username = normalized.username,
                password = normalized.password,
                port = normalized.port ?: 445
            )
            if (result.isSuccess) {
                ResourceConnectionTestResult(ResourceConnectionStatus.SUCCESS)
            } else {
                ResourceConnectionTestResult(
                    status = ResourceConnectionStatus.FAILED,
                    errorCode = ResourceErrorCode.UNREACHABLE,
                    diagnosticMessage = result.exceptionOrNull()?.message
                )
            }
        },
        ResourceType.SFTP to SftpResourceStrategy { form ->
            val normalized = normalizeForStrategy(form)
            val result = smbOperationsUseCase.testSftpConnection(
                host = normalized.host,
                port = normalized.port ?: 22,
                username = normalized.username,
                password = normalized.password
            )
            if (result.isSuccess) {
                ResourceConnectionTestResult(ResourceConnectionStatus.SUCCESS)
            } else {
                ResourceConnectionTestResult(
                    status = ResourceConnectionStatus.FAILED,
                    errorCode = ResourceErrorCode.UNREACHABLE,
                    diagnosticMessage = result.exceptionOrNull()?.message
                )
            }
        },
        ResourceType.FTP to FtpResourceStrategy { form ->
            val normalized = normalizeForStrategy(form)
            val result = smbOperationsUseCase.testFtpConnection(
                host = normalized.host,
                port = normalized.port ?: 21,
                username = normalized.username,
                password = normalized.password
            )
            if (result.isSuccess) {
                ResourceConnectionTestResult(ResourceConnectionStatus.SUCCESS)
            } else {
                ResourceConnectionTestResult(
                    status = ResourceConnectionStatus.FAILED,
                    errorCode = ResourceErrorCode.UNREACHABLE,
                    diagnosticMessage = result.exceptionOrNull()?.message
                )
            }
        },
        ResourceType.CLOUD to CloudResourceStrategy { form ->
            val normalized = normalizeForStrategy(form)
            val candidate = buildPersistenceModel(normalized)
            val result = resourceRepository.testConnection(candidate)
            if (result.isSuccess) {
                ResourceConnectionTestResult(ResourceConnectionStatus.SUCCESS)
            } else {
                ResourceConnectionTestResult(
                    status = ResourceConnectionStatus.FAILED,
                    errorCode = ResourceErrorCode.UNREACHABLE,
                    diagnosticMessage = result.exceptionOrNull()?.message
                )
            }
        }
    )

    suspend fun initialize(
        mode: ResourceEditorMode,
        resourceType: ResourceType = ResourceType.LOCAL,
        resourceId: Long? = null
    ): Result<ResourceFormData> = withContext(ioDispatcher) {
        try {
            when (mode) {
                ResourceEditorMode.CREATE -> {
                    val defaultRememberFileList = settingsRepository
                        .getSettings()
                        .first()
                        .defaultRememberFileList
                    Result.success(
                        ResourceFormData(
                            mode = ResourceEditorMode.CREATE,
                            type = resourceType,
                            rememberFileList = defaultRememberFileList
                        )
                    )
                }

                ResourceEditorMode.EDIT,
                ResourceEditorMode.COPY -> {
                    val id = resourceId ?: return@withContext Result.failure(
                        IllegalArgumentException("resourceId is required for mode $mode")
                    )
                    val existing = resourceRepository.getResourceById(id)
                        ?: return@withContext Result.failure(
                            IllegalArgumentException("Resource with id=$id not found")
                        )

                    val form = toFormData(existing, mode)
                    Result.success(form)
                }
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun validate(formData: ResourceFormData): ResourceValidationResult {
        val strategy = strategyFor(formData.type)
        return strategy.validate(formData)
    }

    suspend fun testConnection(formData: ResourceFormData): ResourceConnectionTestResult {
        val strategy = strategyFor(formData.type)
        return strategy.testConnection(formData)
    }

    fun fieldSchema(resourceType: ResourceType): List<ResourceFieldSchema> {
        return strategyFor(resourceType).fieldSchema()
    }

    fun buildPersistenceModel(formData: ResourceFormData): MediaResource {
        val normalized = normalizeForStrategy(formData)
        val resourcePath = buildResourcePath(normalized)

        return MediaResource(
            id = normalized.id ?: 0L,
            name = normalized.name,
            path = resourcePath,
            type = normalized.type,
            credentialsId = normalized.credentialsId,
            cloudProvider = normalized.cloudProvider,
            cloudFolderId = normalized.cloudFolderId,
            supportedMediaTypes = normalized.supportedMediaTypes,
            sortMode = normalized.sortMode,
            displayMode = normalized.displayMode,
            slideshowInterval = normalized.slideshowInterval,
            isDestination = normalized.isDestination,
            destinationOrder = normalized.destinationOrder,
            destinationColor = normalized.destinationColor,
            isReadOnly = normalized.isReadOnly,
            showCommandPanel = normalized.showCommandPanel,
            scanSubdirectories = normalized.scanSubdirectories,
            disableThumbnails = normalized.disableThumbnails,
            allFiles = normalized.allFiles,
            showHiddenFiles = normalized.showHiddenFiles,
            showSubfoldersAsItems = normalized.showSubfoldersAsItems,
            rememberFileList = normalized.rememberFileList,
            accessPin = normalized.accessPin.ifBlank { null },
            comment = normalized.comment.ifBlank { null }
        )
    }

    suspend fun save(formData: ResourceFormData): Result<ResourceEditorSaveResult> = withContext(ioDispatcher) {
        try {
            val validation = validate(formData)
            if (!validation.isValid) {
                return@withContext Result.failure(IllegalStateException("Resource form validation failed"))
            }

            val preparedFormData = ensureDestinationMetadata(formData)
            val model = buildPersistenceModel(preparedFormData)

            val resourceId: Long = when {
                formData.mode == ResourceEditorMode.EDIT -> {
                    val existingId = requireNotNull(preparedFormData.id) {
                        "resourceId is required for EDIT mode"
                    }
                    updateResourceUseCase(model).getOrThrow()
                    existingId
                }

                else -> {
                    addResourceUseCase(model).getOrThrow()
                }
            }

            updateVerificationStatus(resourceId, ResourceVerificationStatus.PENDING_VERIFICATION)
            launchPostSaveVerification(resourceId)

            Result.success(
                ResourceEditorSaveResult(
                    resourceId = resourceId,
                    verificationStatus = ResourceVerificationStatus.PENDING_VERIFICATION
                )
            )
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun strategyFor(type: ResourceType): ResourceStrategy {
        return strategies[type] ?: LocalResourceStrategy()
    }

    private suspend fun ensureDestinationMetadata(formData: ResourceFormData): ResourceFormData {
        if (!formData.isDestination) {
            return formData.copy(destinationOrder = null)
        }

        val currentOrder = formData.destinationOrder
        if (currentOrder != null && currentOrder >= 0) {
            return formData
        }

        val maxExistingOrder = resourceRepository.getAllResourcesSync()
            .asSequence()
            .filter { resource ->
                resource.isDestination &&
                    (resource.destinationOrder ?: -1) >= 0 &&
                    (formData.id == null || resource.id != formData.id)
            }
            .map { it.destinationOrder ?: -1 }
            .maxOrNull() ?: -1

        val nextOrder = maxExistingOrder + 1
        return formData.copy(
            destinationOrder = nextOrder,
            destinationColor = DestinationColors.getColorForDestination(nextOrder)
        )
    }

    private fun normalizeForStrategy(formData: ResourceFormData): ResourceFormData {
        return strategyFor(formData.type).normalizeBeforeSave(formData)
    }

    private fun buildResourcePath(formData: ResourceFormData): String {
        return when (formData.type) {
            ResourceType.LOCAL -> formData.path

            ResourceType.SMB -> {
                val normalized = formData.path.trim().replace('\\', '/').trim('/')
                val parts = normalized.split('/', limit = 2)
                val share = parts.getOrElse(0) { "" }
                val remotePath = parts.getOrElse(1) { "" }
                SmbPathUtils.buildSmbPath(
                    server = formData.host,
                    share = share,
                    path = remotePath,
                    port = formData.port ?: 445
                )
            }

            ResourceType.SFTP -> SftpPathUtils.buildSftpPath(
                host = formData.host,
                path = formData.path,
                port = formData.port ?: 22
            )

            ResourceType.FTP -> FtpPathUtils.buildFtpPath(
                host = formData.host,
                path = formData.path,
                port = formData.port ?: 21
            )

            ResourceType.CLOUD -> formData.path
        }
    }

    private fun launchPostSaveVerification(resourceId: Long) {
        scope.launch {
            try {
                val resource = resourceRepository.getResourceById(resourceId)
                    ?: run {
                        updateVerificationStatus(resourceId, ResourceVerificationStatus.NEEDS_ATTENTION)
                        return@launch
                    }

                val connectionResult = resourceRepository.testConnection(resource)
                if (connectionResult.isSuccess) {
                    updateVerificationStatus(resourceId, ResourceVerificationStatus.VERIFIED)
                } else {
                    updateVerificationStatus(resourceId, ResourceVerificationStatus.NEEDS_ATTENTION)
                }
            } catch (error: Exception) {
                Timber.w(error, "Post-save verification failed for resourceId=$resourceId")
                updateVerificationStatus(resourceId, ResourceVerificationStatus.NEEDS_ATTENTION)
            }
        }
    }

    private fun updateVerificationStatus(resourceId: Long, status: ResourceVerificationStatus) {
        _verificationStatuses.value = _verificationStatuses.value.toMutableMap().apply {
            put(resourceId, status)
        }
    }

    private suspend fun toFormData(resource: MediaResource, mode: ResourceEditorMode): ResourceFormData {
        val baseType = resource.type
        val sourcePath = resource.path

        var parsedPort: Int? = null

        val hostAndPath = when (baseType) {
            ResourceType.SMB -> {
                val parsed = SmbPathUtils.parseSmbPath(sourcePath)
                val smbPath = if (parsed != null) {
                    parsedPort = parsed.connectionInfo.port
                    listOf(parsed.connectionInfo.shareName, parsed.remotePath)
                        .filter { it.isNotBlank() }
                        .joinToString("/")
                } else {
                    sourcePath
                }
                parsed?.connectionInfo?.server.orEmpty() to smbPath
            }

            ResourceType.SFTP -> {
                val parsed = SftpPathUtils.parseSftpPath(sourcePath)
                parsedPort = parsed?.port
                parsed?.host.orEmpty() to (parsed?.remotePath ?: sourcePath)
            }

            ResourceType.FTP -> {
                val parsed = FtpPathUtils.parseFtpPath(sourcePath)
                parsedPort = parsed?.port
                parsed?.host.orEmpty() to (parsed?.remotePath ?: sourcePath)
            }

            else -> "" to sourcePath
        }

        // Load credentials from credential store
        var loadedUsername = ""
        var loadedPassword = ""
        val credentialsId = resource.credentialsId
        if (credentialsId != null) {
            try {
                when (baseType) {
                    ResourceType.SMB -> {
                        smbOperationsUseCase.getConnectionInfo(credentialsId).getOrNull()?.let { info ->
                            loadedUsername = info.username
                            loadedPassword = info.password
                        }
                    }
                    ResourceType.SFTP -> {
                        smbOperationsUseCase.getSftpCredentials(credentialsId).getOrNull()?.let { cred ->
                            loadedUsername = cred.username
                            loadedPassword = cred.password
                        }
                    }
                    ResourceType.FTP -> {
                        smbOperationsUseCase.getFtpCredentials(credentialsId).getOrNull()?.let { cred ->
                            loadedUsername = cred.username
                            loadedPassword = cred.password
                        }
                    }
                    else -> { /* no credentials for local/cloud */ }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load credentials for credentialsId=$credentialsId")
            }
        }

        val form = ResourceFormData(
            id = if (mode == ResourceEditorMode.COPY) null else resource.id,
            mode = mode,
            type = baseType,
            name = resource.name,
            path = hostAndPath.second,
            comment = resource.comment.orEmpty(),
            accessPin = resource.accessPin.orEmpty(),
            isDestination = resource.isDestination,
            destinationOrder = resource.destinationOrder,
            destinationColor = resource.destinationColor,
            isReadOnly = resource.isReadOnly,
            credentialsId = resource.credentialsId,
            username = loadedUsername,
            password = loadedPassword,
            host = hostAndPath.first,
            port = parsedPort,
            cloudProvider = resource.cloudProvider,
            cloudFolderId = resource.cloudFolderId,
            supportedMediaTypes = resource.supportedMediaTypes,
            sortMode = resource.sortMode,
            displayMode = resource.displayMode,
            slideshowInterval = resource.slideshowInterval,
            showCommandPanel = resource.showCommandPanel,
            scanSubdirectories = resource.scanSubdirectories,
            disableThumbnails = resource.disableThumbnails,
            allFiles = resource.allFiles,
            showHiddenFiles = resource.showHiddenFiles,
            showSubfoldersAsItems = resource.showSubfoldersAsItems,
            rememberFileList = resource.rememberFileList
        )

        return if (mode == ResourceEditorMode.COPY) {
            form.copy(
                id = null,
                mode = ResourceEditorMode.COPY,
                destinationOrder = null
            )
        } else {
            form
        }
    }

    fun emptyForm(type: ResourceType = ResourceType.LOCAL): ResourceFormData {
        return ResourceFormData(
            mode = ResourceEditorMode.CREATE,
            type = type,
            sortMode = SortMode.NAME_ASC,
            displayMode = DisplayMode.LIST
        )
    }

    suspend fun getExistingResourceNames(excludeResourceId: Long? = null): Set<String> = withContext(ioDispatcher) {
        resourceRepository.getAllResourcesSync()
            .asSequence()
            .filter { excludeResourceId == null || it.id != excludeResourceId }
            .map { it.name.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun generateUniqueCopyName(sourceName: String, existingNames: Set<String>): String {
        val normalized = sourceName.trim().ifBlank { "Resource" }
        val baseCandidate = "$normalized (Copy)"
        if (!existingNames.contains(baseCandidate)) {
            return baseCandidate
        }

        var suffix = 1
        while (true) {
            val candidate = "$normalized (Copy $suffix)"
            if (!existingNames.contains(candidate)) {
                return candidate
            }
            suffix++
        }
    }

    fun buildNameSuggestions(desiredName: String, existingNames: Set<String>, maxCount: Int = 3): List<String> {
        val normalized = desiredName.trim().ifBlank { "Resource" }
        if (!existingNames.contains(normalized)) {
            return listOf(normalized)
        }

        val suggestions = mutableListOf<String>()
        var index = 1
        while (suggestions.size < maxCount) {
            val candidate = "$normalized $index"
            if (!existingNames.contains(candidate)) {
                suggestions.add(candidate)
            }
            index++
        }
        return suggestions
    }

    suspend fun getExistingPathKeys(excludeResourceId: Long? = null): Set<Pair<ResourceType, String>> = withContext(ioDispatcher) {
        resourceRepository.getAllResourcesSync()
            .asSequence()
            .filter { excludeResourceId == null || it.id != excludeResourceId }
            .map { it.type to it.path.trim() }
            .filter { it.second.isNotBlank() }
            .toSet()
    }

    suspend fun getResourceStatistics(resourceId: Long): com.sza.fastmediasorter.ui.resourceeditor.ResourceStatistics? = withContext(ioDispatcher) {
        val resource = resourceRepository.getResourceById(resourceId) ?: return@withContext null
        com.sza.fastmediasorter.ui.resourceeditor.ResourceStatistics(
            fileCount = resource.fileCount,
            subfolderCount = resource.subfolderCount,
            createdDate = resource.createdDate,
            lastBrowseDate = resource.lastBrowseDate,
            lastSyncDate = resource.lastSyncDate,
            readSpeedMbps = resource.readSpeedMbps,
            writeSpeedMbps = resource.writeSpeedMbps
        )
    }
}