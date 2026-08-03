package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.metrics.OperationMetricsRecorder
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
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.strategy.CloudResourceStrategy
import com.sza.fastmediasorter.domain.strategy.FtpResourceStrategy
import com.sza.fastmediasorter.domain.strategy.LocalResourceStrategy
import com.sza.fastmediasorter.domain.strategy.ResourceFieldSchema
import com.sza.fastmediasorter.domain.strategy.ResourceStrategy
import com.sza.fastmediasorter.domain.strategy.SftpResourceStrategy
import com.sza.fastmediasorter.domain.strategy.SmbResourceStrategy
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.utils.FtpPathUtils
import com.sza.fastmediasorter.utils.SftpPathUtils
import com.sza.fastmediasorter.utils.SmbPathUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
    private val cachedFileListRepository: CachedFileListRepository,
    private val addResourceUseCase: AddResourceUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val resolveResourceIconUseCase: ResolveResourceIconUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

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
            OperationMetricsRecorder.recordConnectionTest(ResourceType.SMB, result.isSuccess)
            connectionTestResultFrom(result)
        },
        ResourceType.SFTP to SftpResourceStrategy { form ->
            val normalized = normalizeForStrategy(form)
            val result = smbOperationsUseCase.testSftpConnection(
                host = normalized.host,
                port = normalized.port ?: 22,
                username = normalized.username,
                password = normalized.password
            )
            OperationMetricsRecorder.recordConnectionTest(ResourceType.SFTP, result.isSuccess)
            connectionTestResultFrom(result)
        },
        ResourceType.FTP to FtpResourceStrategy { form ->
            val normalized = normalizeForStrategy(form)
            val result = smbOperationsUseCase.testFtpConnection(
                host = normalized.host,
                port = normalized.port ?: 21,
                username = normalized.username,
                password = normalized.password
            )
            OperationMetricsRecorder.recordConnectionTest(ResourceType.FTP, result.isSuccess)
            connectionTestResultFrom(result)
        },
        ResourceType.CLOUD to CloudResourceStrategy { form ->
            val normalized = normalizeForStrategy(form)
            val candidate = buildPersistenceModel(normalized)
            val result = resourceRepository.testConnection(candidate)
            OperationMetricsRecorder.recordConnectionTest(ResourceType.CLOUD, result.isSuccess)
            connectionTestResultFrom(result)
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

    fun validate(formData: ResourceFormData): ResourceValidationResult = strategyFor(formData.type).validate(formData)

    suspend fun testConnection(formData: ResourceFormData): ResourceConnectionTestResult = strategyFor(formData.type).testConnection(formData)

    fun fieldSchema(resourceType: ResourceType): List<ResourceFieldSchema> = strategyFor(resourceType).fieldSchema()

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
            comment = normalized.comment.ifBlank { null },
            profile = normalized.profile,
            // Preserve user-picked or previously-resolved icon; save() handles null case.
            iconId = normalized.iconId
        )
    }

    /**
     * @param verificationScope lifecycle-owned scope (the caller's viewModelScope) on which the
     * fire-and-forget post-save connection check runs. S0730: previously this UseCase held its own
     * never-cancelled SupervisorJob scope, so the network verification leaked past the editor's
     * teardown; binding it to the caller cancels the in-flight check when the screen closes.
     */
    suspend fun save(
        formData: ResourceFormData,
        verificationScope: CoroutineScope
    ): Result<ResourceEditorSaveResult> = withContext(ioDispatcher) {
        try {
            val validation = validate(formData)
            if (!validation.isValid) {
                return@withContext Result.failure(IllegalStateException("Resource form validation failed"))
            }

            val preparedFormData = ensureDestinationMetadata(formData)
            // Normalize fields before persisting credentials so lookup keys (server, port) are canonical.
            val normalizedFormData = normalizeForStrategy(preparedFormData)
            // Persist credentials before building the model so credentialsId is embedded.
            val formDataWithCredentials = persistNetworkCredentials(normalizedFormData)
            var model = buildPersistenceModel(formDataWithCredentials)

            val resourceId: Long = when {
                formData.mode == ResourceEditorMode.EDIT -> {
                    val existingId = requireNotNull(preparedFormData.id) {
                        "resourceId is required for EDIT mode"
                    }
                    // Preserve runtime-only fields that are not part of the edit form.
                    // Without this, isWritable would be overwritten with the default (false),
                    // causing Move/Rename/Delete buttons to disappear after editing a resource.
                    val existing = resourceRepository.getResourceById(existingId)
                    if (existing != null) {
                        model = model.copy(
                            isWritable = existing.isWritable,
                            isAvailable = existing.isAvailable,
                            fileCount = existing.fileCount,
                            subfolderCount = existing.subfolderCount,
                            lastViewedFile = existing.lastViewedFile,
                            lastScrollPosition = existing.lastScrollPosition,
                            lastAccessedDate = existing.lastAccessedDate,
                            lastBrowseDate = existing.lastBrowseDate,
                            lastSyncDate = existing.lastSyncDate,
                            displayOrder = existing.displayOrder,
                            readSpeedMbps = existing.readSpeedMbps,
                            writeSpeedMbps = existing.writeSpeedMbps,
                            recommendedThreads = existing.recommendedThreads,
                            lastSpeedTestDate = existing.lastSpeedTestDate
                        )
                    }
                    updateResourceUseCase(model).getOrThrow()
                    // Invalidate caches when the saved settings reshape the list - a stale root-only
                    // list must not survive a scan-depth change, and a list built in the old
                    // subfolder mode must not survive a showSubfoldersAsItems change (S1315).
                    val depthChanged = existing != null && existing.scanSubdirectories != model.scanSubdirectories
                    val itemModeChanged =
                        existing != null && existing.showSubfoldersAsItems != model.showSubfoldersAsItems
                    if (depthChanged || itemModeChanged) {
                        MediaFilesCacheManager.clearCache(existingId)
                        cachedFileListRepository.deleteCachedFiles(existingId)
                    }
                    existingId
                }

                else -> {
                    // Assign an initial icon for newly added resources if not already set.
                    // The user can override via the icon picker (S0034 Phase 06).
                    if (model.iconId == null) {
                        model = model.copy(
                            iconId = resolveResourceIconUseCase(
                                path = model.path,
                                profile = model.profile,
                                type = model.type
                            )
                        )
                    }
                    addResourceUseCase(model).getOrThrow()
                }
            }

            updateVerificationStatus(resourceId, ResourceVerificationStatus.PENDING_VERIFICATION)
            launchPostSaveVerification(resourceId, verificationScope)

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

    private fun strategyFor(type: ResourceType): ResourceStrategy = strategies[type] ?: LocalResourceStrategy()

    private fun connectionTestResultFrom(result: Result<*>) = if (result.isSuccess)
        ResourceConnectionTestResult(ResourceConnectionStatus.SUCCESS)
    else
        ResourceConnectionTestResult(
            status = ResourceConnectionStatus.FAILED,
            errorCode = ResourceErrorCode.UNREACHABLE
        )

    /**
     * Persists network credentials (SMB/SFTP/FTP) and returns a copy with credentialsId set.
     * EDIT mode (credentialsId present): updates in-place by UUID to avoid stale lookups.
     * CREATE mode: delegates to SmbOperationsUseCase (insert-or-update by server+share key).
     */
    private suspend fun persistNetworkCredentials(formData: ResourceFormData): ResourceFormData {
        if (formData.type !in setOf(ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP)) {
            return formData
        }

        val existingCredentialId = formData.credentialsId
        if (existingCredentialId != null) {
            // EDIT: update in-place by UUID; skip server+share lookup that may miss.
            return updateCredentialInPlace(formData, existingCredentialId)
        }

        // CREATE: delegate to SmbOperationsUseCase (insert-or-update by server key)
        val credentialId: String? = when (formData.type) {
            ResourceType.SMB -> {
                Timber.d("persistNetworkCredentials: CREATE SMB for host=${formData.host}")
                smbOperationsUseCase.saveCredentials(
                    server = formData.host,
                    shareName = formData.path,
                    username = formData.username,
                    password = formData.password,
                    domain = "", // ResourceFormData has no domain field; SMB domain not exposed in the editor
                    port = formData.port ?: 445
                ).onFailure { e -> Timber.e(e, "persistNetworkCredentials: SMB insert failed") }
                    .getOrNull()
            }
            ResourceType.SFTP -> {
                Timber.d("persistNetworkCredentials: CREATE SFTP for host=${formData.host}")
                smbOperationsUseCase.saveSftpCredentials(
                    host = formData.host,
                    port = formData.port ?: 22,
                    username = formData.username,
                    password = formData.password
                ).onFailure { e -> Timber.e(e, "persistNetworkCredentials: SFTP insert failed") }
                    .getOrNull()
            }
            ResourceType.FTP -> {
                Timber.d("persistNetworkCredentials: CREATE FTP for host=${formData.host}")
                smbOperationsUseCase.saveFtpCredentials(
                    host = formData.host,
                    port = formData.port ?: 21,
                    username = formData.username,
                    password = formData.password
                ).onFailure { e -> Timber.e(e, "persistNetworkCredentials: FTP insert failed") }
                    .getOrNull()
            }
            else -> null
        }
        return if (credentialId != null) formData.copy(credentialsId = credentialId) else formData
    }

    /** Updates an existing credential in-place by UUID; falls back to insert if not found. */
    private suspend fun updateCredentialInPlace(
        formData: ResourceFormData,
        credentialId: String
    ): ResourceFormData {
        return try {
            val existing = credentialsRepository.getByCredentialId(credentialId)
            if (existing == null) {
                Timber.w("updateCredentialInPlace: $credentialId not found, falling back to insert")
                return persistNetworkCredentials(formData.copy(credentialsId = null))
            }
            val defaultPort = when (formData.type) {
                ResourceType.SMB -> 445
                ResourceType.SFTP -> 22
                ResourceType.FTP -> 21
                else -> existing.port
            }
            // Only re-encrypt when user actually typed something; otherwise keep the old hash.
            val newEncryptedPwd = if (formData.password.isNotEmpty()) {
                CryptoHelper.encrypt(formData.password) ?: existing.encryptedPassword
            } else {
                existing.encryptedPassword
            }
            // For SMB, extract bare share name from "share/subfolder" path (first segment only).
            // For SFTP/FTP there is no shareName concept, preserve existing value.
            val newShareName = if (formData.type == ResourceType.SMB) {
                formData.path.replace('\\', '/').split('/').firstOrNull()?.takeIf { it.isNotEmpty() }
                    ?: existing.shareName
            } else {
                existing.shareName
            }
            val updated = existing.copy(
                server = formData.host,
                port = formData.port ?: defaultPort,
                username = formData.username,
                encryptedPassword = newEncryptedPwd,
                shareName = newShareName,
                domain = existing.domain // ResourceFormData has no domain field; preserve existing value
            )
            credentialsRepository.update(updated)
            Timber.d("updateCredentialInPlace: OK id=$credentialId type=${formData.type} host=${formData.host} shareName=$newShareName pwdChanged=${formData.password.isNotEmpty()}")
            formData // credentialsId is already correct
        } catch (e: Exception) {
            Timber.e(e, "updateCredentialInPlace: failed for $credentialId")
            formData
        }
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

    private fun normalizeForStrategy(formData: ResourceFormData) = strategyFor(formData.type).normalizeBeforeSave(formData)

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
            ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> formData.path
        }
    }

    private fun launchPostSaveVerification(resourceId: Long, scope: CoroutineScope) {
        // ioDispatcher keeps the network check off Main even though the caller's scope is Main-bound.
        scope.launch(ioDispatcher) {
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
            rememberFileList = resource.rememberFileList,
            profile = resource.profile,
            iconId = resource.iconId
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
        val cachedFiles = cachedFileListRepository.getCachedFiles(resourceId)
        val inferredSubfolders = cachedFiles?.let { inferSubfolderCount(resource, it) } ?: 0
        val effectiveSubfolderCount = maxOf(resource.subfolderCount, inferredSubfolders)

        com.sza.fastmediasorter.ui.resourceeditor.ResourceStatistics(
            fileCount = resource.fileCount,
            subfolderCount = effectiveSubfolderCount,
            createdDate = resource.createdDate,
            lastBrowseDate = resource.lastBrowseDate,
            lastSyncDate = resource.lastSyncDate,
            readSpeedMbps = resource.readSpeedMbps,
            writeSpeedMbps = resource.writeSpeedMbps
        )
    }

    private fun inferSubfolderCount(resource: MediaResource, files: List<com.sza.fastmediasorter.domain.model.MediaFile>): Int {
        if (files.isEmpty()) return 0

        val rootPath = normalizePath(resource.path)
        val directories = mutableSetOf<String>()

        files.forEach { file ->
            val path = normalizePath(file.path)
            val parent = extractParent(path)

            if (!parent.isNullOrBlank()) {
                val isUnderRoot = rootPath.isNotBlank() && (parent == rootPath || parent.startsWith("$rootPath/"))
                if (rootPath.isBlank() || isUnderRoot) {
                    directories.add(parent)
                }
            }

            if (file.isDirectory) {
                directories.add(path)
            }
        }

        if (rootPath.isNotBlank()) {
            directories.remove(rootPath)
        }
        return directories.size
    }

    private fun normalizePath(path: String) = path.trim().trimEnd('/').replace("\\\\", "/")

    private fun extractParent(path: String): String? {
        val idx = path.lastIndexOf('/')
        return if (idx <= 0) null else path.substring(0, idx).trimEnd('/')
    }
}