package com.sza.fastmediasorter.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.StorageVolumeRepository
import com.sza.fastmediasorter.domain.scanner.WearWatchMediaScanner
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.utils.FtpPathUtils
import com.sza.fastmediasorter.utils.SftpPathUtils
import com.sza.fastmediasorter.utils.SmbPathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResourceRepositoryImpl @Inject constructor(
    private val resourceDao: ResourceDao,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val storageVolumeRepository: StorageVolumeRepository,
    // S1861: the interface, so this class in src/main stays free of a build-variant check - the
    // flavors with no Wear companion bind the inert wearStub twin instead.
    private val wearWatchMediaScanner: WearWatchMediaScanner
) : ResourceRepository {

    override fun getAllResources(): Flow<List<MediaResource>> {
        return combine(
            resourceDao.getAllResources(),
            credentialsRepository.getAllCredentials()
        ) { resources, credentials ->
            val credMap = credentials.associateBy { it.credentialId }
            resources.map { entity ->
                val accountId = entity.credentialsId?.let { credMap[it]?.accountId }
                entity.toDomain(accountId)
            }
        }.map { applyVolumeAvailability(it) }
    }

    override suspend fun getAllResourcesSync(): List<MediaResource> {
        val entities = resourceDao.getAllResourcesSync()
        val credentials = credentialsRepository.getAllCredentials().first()
        val credMap = credentials.associateBy { it.credentialId }
        return applyVolumeAvailability(
            entities.map { entity ->
                val accountId = entity.credentialsId?.let { credMap[it]?.accountId }
                entity.toDomain(accountId)
            }
        )
    }

    /**
     * S1378: `isAvailable` is DERIVED for a volume-bound resource - [applyVolumeAvailability] forces
     * it false while the volume is unmounted, and every caller that edits an unrelated field does so
     * on a `copy()` of that loaded object. Writing the derived value back would make an eject
     * permanent: nothing sets the stored flag true again when the card returns, so the resource would
     * stay dead after reinsertion. Keep what is on disk for a bound resource; an unbound resource
     * still authors the field exactly as before.
     */
    private suspend fun ResourceEntity.withStoredAvailabilityIfVolumeBound(): ResourceEntity {
        if (storageVolumeId == null) return this
        val stored = resourceDao.getResourceByIdSync(id)
        return if (stored == null) this else copy(isAvailable = stored.isAvailable)
    }

    /**
     * S1378: a resource bound to a volume that is gone or unmounted reads as unavailable, so an
     * ejected card shows a clear state instead of an empty folder. Only the in-memory flag is
     * touched - the stored binding and the granted access outlive the eject, which is what lets the
     * resource come back by itself when the card is reinserted.
     *
     * The volume registry re-enumerates and runs StatFs per mounted volume on every call, so the
     * unbound case (every install until a volume-bound resource exists) must not pay for it.
     */
    private suspend fun applyVolumeAvailability(resources: List<MediaResource>): List<MediaResource> {
        if (resources.none { it.storageVolumeId != null }) return resources
        val mountedIds = storageVolumeRepository.getVolumes()
            .filter { it.isMounted }
            .mapTo(mutableSetOf()) { it.id }
        return resources.map { resource ->
            val boundVolumeId = resource.storageVolumeId
            if (boundVolumeId != null && boundVolumeId !in mountedIds) {
                resource.copy(isAvailable = false)
            } else {
                resource
            }
        }
    }
    
    override suspend fun getResourceById(id: Long): MediaResource? {
        val entity = resourceDao.getResourceByIdSync(id) ?: return null
        val accountId = entity.credentialsId?.let { credentialsRepository.getByCredentialId(it)?.accountId }
        return entity.toDomain(accountId)
    }

    override suspend fun getLocalResourceByPath(path: String): MediaResource? {
        val entity = resourceDao.getLocalResourceByPathSync(path) ?: return null
        val accountId = entity.credentialsId?.let { credentialsRepository.getByCredentialId(it)?.accountId }
        return entity.toDomain(accountId)
    }
    
    override fun getResourcesByType(type: ResourceType): Flow<List<MediaResource>> {
        return combine(
            resourceDao.getResourcesByType(type),
            credentialsRepository.getAllCredentials()
        ) { resources, credentials ->
            val credMap = credentials.associateBy { it.credentialId }
            resources.map { entity ->
                val accountId = entity.credentialsId?.let { credMap[it]?.accountId }
                entity.toDomain(accountId)
            }
        }.map { applyVolumeAvailability(it) }
    }

    override fun getDestinations(): Flow<List<MediaResource>> {
        return combine(
            resourceDao.getDestinations(),
            credentialsRepository.getAllCredentials()
        ) { resources, credentials ->
            val credMap = credentials.associateBy { it.credentialId }
            resources.map { entity ->
                val accountId = entity.credentialsId?.let { credMap[it]?.accountId }
                entity.toDomain(accountId)
            }
        }.map { applyVolumeAvailability(it) }
    }
    
    override suspend fun getFilteredResources(
        filterByType: Set<ResourceType>?,
        filterByMediaType: Set<MediaType>?,
        filterByName: String?,
        sortMode: SortMode
    ): List<MediaResource> {
        // Optimization: Use FTS4 if searching by name
        if (!filterByName.isNullOrBlank()) {
            // Append * for prefix matching (e.g. "vid" -> "vid*")
            val ftsQuery = "$filterByName*"
            Timber.d("getFilteredResources: Using FTS search with query='$ftsQuery'")
            
            val searchResults = withContext(Dispatchers.IO) {
                resourceDao.searchResourcesFts(ftsQuery)
            }
            
            val credentials = credentialsRepository.getAllCredentials().first()
            val credMap = credentials.associateBy { it.credentialId }
            
            // Apply other filters in memory (fast enough for <10k items)
            return searchResults.asSequence()
                .map { entity ->
                    val accountId = entity.credentialsId?.let { credMap[it]?.accountId }
                    entity.toDomain(accountId)
                }
                .filter { resource ->
                    // Filter by Type
                    if (filterByType != null && filterByType.isNotEmpty()) {
                        if (resource.type !in filterByType) return@filter false
                    }
                    
                    // Filter by Media Type
                    if (filterByMediaType != null && filterByMediaType.isNotEmpty()) {
                        // Check intersection of supported types
                        val hasCommonType = resource.supportedMediaTypes.any { it in filterByMediaType }
                        if (!hasCommonType) return@filter false
                    }
                    
                    true
                }
                .toList()
                // S0549: RANDOM must not go through a Comparator - a comparator returning random
                // signs violates the general contract (non-transitive) and TimSort throws
                // "Comparison method violates its general contract" on larger lists. Use a proper
                // Fisher-Yates shuffle, consistent with the file-list RANDOM implementations.
                .let { if (sortMode == SortMode.RANDOM) it.shuffled() else it.sortedWith(getComparator(sortMode)) }
        }

        // Standard SQL query for non-search filtering
        val whereConditions = mutableListOf<String>()
        val args = mutableListOf<Any>()
        
        // Filter by resource type
        if (filterByType != null && filterByType.isNotEmpty()) {
            val placeholders = filterByType.joinToString(",") { "?" }
            whereConditions.add("type IN ($placeholders)")
            args.addAll(filterByType.map { it.name })
        }
        
        // Filter by media types (using bitwise AND on supportedMediaTypesFlags)
        if (filterByMediaType != null && filterByMediaType.isNotEmpty()) {
            // Calculate required flags
            var requiredFlags = 0
            if (MediaType.IMAGE in filterByMediaType) requiredFlags = requiredFlags or 0b0001
            if (MediaType.VIDEO in filterByMediaType) requiredFlags = requiredFlags or 0b0010
            if (MediaType.AUDIO in filterByMediaType) requiredFlags = requiredFlags or 0b0100
            if (MediaType.GIF in filterByMediaType) requiredFlags = requiredFlags or 0b1000
            if (MediaType.TEXT in filterByMediaType) requiredFlags = requiredFlags or 0b00010000
            if (MediaType.PDF in filterByMediaType) requiredFlags = requiredFlags or 0b00100000
            if (MediaType.EPUB in filterByMediaType) requiredFlags = requiredFlags or 0b01000000
            if (MediaType.OFFICE_DOCUMENT in filterByMediaType) requiredFlags = requiredFlags or 0b10000000
            
            // Resource matches if ANY of the selected media types are supported
            whereConditions.add("(supportedMediaTypesFlags & ?) > 0")
            args.add(requiredFlags)
        }
        
        // Filter by name substring
        if (filterByName != null && filterByName.isNotBlank()) {
            whereConditions.add("(name LIKE ? OR path LIKE ?)")
            val pattern = "%$filterByName%"
            args.add(pattern)
            args.add(pattern)
        }
        
        val whereClause = if (whereConditions.isNotEmpty()) {
            "WHERE " + whereConditions.joinToString(" AND ")
        } else {
            ""
        }
        
        val orderBy = when (sortMode) {
            SortMode.MANUAL -> "ORDER BY displayOrder ASC"
            SortMode.NAME_ASC -> "ORDER BY name COLLATE NOCASE ASC"
            SortMode.NAME_DESC -> "ORDER BY name COLLATE NOCASE DESC"
            SortMode.DATE_ASC -> "ORDER BY createdDate ASC"
            SortMode.DATE_DESC -> "ORDER BY createdDate DESC"
            SortMode.SIZE_ASC -> "ORDER BY fileCount ASC"
            SortMode.SIZE_DESC -> "ORDER BY fileCount DESC"
            SortMode.TYPE_ASC -> "ORDER BY type ASC"
            SortMode.TYPE_DESC -> "ORDER BY type DESC"
            SortMode.ARTIST_ASC,
            SortMode.TITLE_ASC,
            SortMode.DURATION_ASC,
            SortMode.DATE_TAKEN_ASC -> "ORDER BY name COLLATE NOCASE ASC"
            SortMode.ARTIST_DESC,
            SortMode.TITLE_DESC,
            SortMode.DURATION_DESC,
            SortMode.DATE_TAKEN_DESC -> "ORDER BY name COLLATE NOCASE DESC"
            SortMode.RANDOM -> "ORDER BY RANDOM()" // SQL random ordering
        }
        
        val sql = "SELECT * FROM resources $whereClause $orderBy"
        
        Timber.d("getFilteredResources: SQL=$sql, args=$args")
        
        val query = SimpleSQLiteQuery(sql, args.toTypedArray())
        val entities = withContext(Dispatchers.IO) {
            resourceDao.getResourcesRaw(query)
        }

        val credentials = credentialsRepository.getAllCredentials().first()
        val credMap = credentials.associateBy { it.credentialId }

        // S1009: hide ad-hoc local folders from browse (type/media/sort). The FTS name-search branch
        // above stays unfiltered - resource search may surface hidden rows per owner decision.
        return applyVolumeAvailability(
            entities.map { entity ->
                val accountId = entity.credentialsId?.let { credMap[it]?.accountId }
                entity.toDomain(accountId)
            }.filterNot { it.isHidden }
        )
    }

    private fun getComparator(sortMode: SortMode): Comparator<MediaResource> {
        return when (sortMode) {
            SortMode.MANUAL -> compareBy { it.displayOrder }
            SortMode.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortMode.NAME_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortMode.DATE_ASC -> compareBy { it.createdDate }
            SortMode.DATE_DESC -> compareByDescending { it.createdDate }
            SortMode.SIZE_ASC -> compareBy { it.fileCount }
            SortMode.SIZE_DESC -> compareByDescending { it.fileCount }
            SortMode.TYPE_ASC -> compareBy { it.type }
            SortMode.TYPE_DESC -> compareByDescending { it.type }
            SortMode.ARTIST_ASC,
            SortMode.TITLE_ASC,
            SortMode.DURATION_ASC,
            SortMode.DATE_TAKEN_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortMode.ARTIST_DESC,
            SortMode.TITLE_DESC,
            SortMode.DURATION_DESC,
            SortMode.DATE_TAKEN_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
            // S0549: RANDOM is handled by .shuffled() at the call site, never via this comparator
            // (a random-sign comparator violates the Comparator contract). Stable no-op keeps the
            // `when` exhaustive; the call site guarantees this branch is unreachable.
            SortMode.RANDOM -> Comparator { _, _ -> 0 }
        }
    }
    
    override suspend fun addResource(resource: MediaResource): Long {
        return resourceDao.insert(resource.toEntity())
    }
    
    override suspend fun updateResource(resource: MediaResource) {
        val entity = resource.toEntity().withStoredAvailabilityIfVolumeBound()
        Timber.d("🔶 SORT_DEBUG Repository.updateResource: id=${entity.id}, name=${entity.name}, sortMode=${entity.sortMode}, displayMode=${entity.displayMode}")
        resourceDao.update(entity)
        Timber.d("🔶 SORT_DEBUG Repository.updateResource: COMPLETED for id=${entity.id}, sortMode=${entity.sortMode}")
    }
    
    override suspend fun swapResourceDisplayOrders(resource1: MediaResource, resource2: MediaResource) {
        resourceDao.swapDisplayOrders(
            id1 = resource1.id,
            order1 = resource1.displayOrder,
            id2 = resource2.id,
            order2 = resource2.displayOrder
        )
    }

    override suspend fun updateResourcesDisplayOrder(resources: List<MediaResource>) {
        val orders = resources.mapIndexed { index, resource -> resource.id to index }
        resourceDao.updateAllDisplayOrders(orders)
    }
    
    override suspend fun deleteResource(resourceId: Long) {
        resourceDao.deleteById(resourceId)
    }

    override suspend fun deleteResourceIfHidden(resourceId: Long) {
        // S1009: only ad-hoc hidden rows may be auto-removed; a visible user resource is never touched.
        val entity = resourceDao.getResourceByIdSync(resourceId) ?: return
        if (entity.isHidden) {
            resourceDao.deleteById(resourceId)
        }
    }
    
    override suspend fun deleteAllResources() {
        resourceDao.deleteAll()
    }

    override suspend fun updateIcon(resourceId: Long, iconId: String?) {
        resourceDao.updateIcon(resourceId, iconId)
    }

    override suspend fun updateLastViewedFile(resourceId: Long, path: String?) {
        resourceDao.updateLastViewedFile(resourceId, path)
    }

    override suspend fun updateLastScrollPosition(resourceId: Long, position: Int) {
        resourceDao.updateLastScrollPosition(resourceId, position)
    }

    override suspend fun backfillMissingIcons(
        resolveIcon: (path: String, profileName: String, typeName: String) -> String?
    ): Int {
        val nullIconEntities = resourceDao.findResourcesWithoutIcon()
        if (nullIconEntities.isEmpty()) return 0
        var updated = 0
        nullIconEntities.forEach { entity ->
            val iconId = resolveIcon(entity.path, entity.profile, entity.type.name)
            if (iconId != null) {
                resourceDao.updateIcon(entity.id, iconId)
                updated++
            }
        }
        Timber.d("backfillMissingIcons: updated $updated resources")
        return updated
    }

    override suspend fun testConnection(resource: MediaResource): Result<String> {
        return when (resource.type) {
            ResourceType.LOCAL -> {
                // Local resources don't need connection testing
                Result.success("Local resource - no connection test needed")
            }
            ResourceType.SMB -> {
                testSmbConnection(resource)
            }
            ResourceType.SFTP -> {
                testSftpConnection(resource)
            }
            ResourceType.FTP -> {
                testFtpConnection(resource)
            }
            ResourceType.CLOUD -> {
                // Not yet implemented
                Result.success("Connection test not yet implemented for ${resource.type}")
            }
            ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> {
                Result.success("Stream resource - no connection test needed")
            }
            ResourceType.WEAR_WATCH -> {
                // S1861: the answer the user needs is whether the bridge has a watch on the far end;
                // a watch that is off the wrist is unreachable no matter what the phone's network says.
                if (wearWatchMediaScanner.isWatchReachable()) {
                    Result.success("Paired watch connected")
                } else {
                    Result.failure(IllegalStateException("No paired watch is currently connected"))
                }
            }
        }
    }
    
    private suspend fun testSmbConnection(resource: MediaResource): Result<String> {
        val credentialsId = resource.credentialsId
        if (credentialsId == null) {
            Timber.w("SMB resource has no credentials ID")
            return Result.failure(Exception("No credentials configured for this SMB resource"))
        }
        
        // Get connection info from credentials
        val connectionInfoResult = smbOperationsUseCase.getConnectionInfo(credentialsId)
        if (connectionInfoResult.isFailure) {
            val error = connectionInfoResult.exceptionOrNull()
            Timber.e(error, "Failed to get SMB connection info")
            return Result.failure(error ?: Exception("Failed to get connection info"))
        }
        
        val connectionInfo = connectionInfoResult.getOrNull()
            ?: return Result.failure(Exception("Connection info is null after successful result"))

        // Derive shareName from the stored resource path (authoritative source) to self-heal
        // stale credentials where shareName was not updated on EDIT (e.g. renamed share).
        val parsedPath = SmbPathUtils.parseSmbPath(resource.path)
        val effectiveShareName = parsedPath?.connectionInfo?.shareName
            ?.takeIf { it.isNotEmpty() } ?: connectionInfo.shareName
        if (effectiveShareName != connectionInfo.shareName) {
            Timber.w(
                "testSmbConnection: credentials shareName='${connectionInfo.shareName}' " +
                "differs from path shareName='$effectiveShareName' - using path value"
            )
        }

        // Test connection
        return smbOperationsUseCase.testConnection(
            server = connectionInfo.server,
            shareName = effectiveShareName,
            username = connectionInfo.username,
            password = connectionInfo.password,
            domain = connectionInfo.domain,
            port = connectionInfo.port
        )
    }
    
    private suspend fun testSftpConnection(resource: MediaResource): Result<String> {
        val credentialsId = resource.credentialsId
        if (credentialsId == null) {
            Timber.w("SFTP resource has no credentials ID")
            return Result.failure(Exception("No credentials configured for this SFTP resource"))
        }
        
        // Get SFTP connection info from credentials
        val credentialsEntity = credentialsRepository.getByCredentialId(credentialsId)
        if (credentialsEntity == null) {
            Timber.w("SFTP credentials not found: $credentialsId")
            return Result.failure(Exception("Credentials not found"))
        }
        
        // Parse SFTP path using utility (handles optional ports)
        val path = resource.path
        val pathInfo = SftpPathUtils.parseSftpPath(path)
        
        if (pathInfo == null) {
            Timber.w("Invalid SFTP path format: $path")
            return Result.failure(Exception("Invalid SFTP path format"))
        }
        
        val host = pathInfo.host
        val port = pathInfo.port
        
        // Test SFTP connection (with password or private key)
        val privateKey = credentialsEntity.decryptedSshPrivateKey
        return smbOperationsUseCase.testSftpConnection(
            host = host,
            port = port,
            username = credentialsEntity.username,
            password = credentialsEntity.password,
            privateKey = privateKey
        )
    }
    
    private suspend fun testFtpConnection(resource: MediaResource): Result<String> {
        val credentialsId = resource.credentialsId
        if (credentialsId == null) {
            Timber.w("FTP resource has no credentials ID")
            return Result.failure(Exception("No credentials configured for this FTP resource"))
        }
        
        val credentialsEntity = credentialsRepository.getByCredentialId(credentialsId)
        
        if (credentialsEntity == null) {
            Timber.w("FTP credentials not found for ID: $credentialsId")
            return Result.failure(Exception("FTP credentials not found"))
        }
        
        // Parse FTP path using utility (handles optional ports)
        val path = resource.path
        val pathInfo = FtpPathUtils.parseFtpPath(path)
        
        if (pathInfo == null) {
            Timber.w("Invalid FTP path format: $path")
            return Result.failure(Exception("Invalid FTP path format"))
        }
        
        val host = pathInfo.host
        val port = pathInfo.port
        
        // Test FTP connection
        return smbOperationsUseCase.testFtpConnection(
            host = host,
            port = port,
            username = credentialsEntity.username,
            password = credentialsEntity.password
        )
    }
    
    private fun ResourceEntity.toDomain(accountId: String? = null): MediaResource {
        val mediaTypes = mutableSetOf<MediaType>()
        if (supportedMediaTypesFlags and 0b0001 != 0) mediaTypes.add(MediaType.IMAGE)
        if (supportedMediaTypesFlags and 0b0010 != 0) mediaTypes.add(MediaType.VIDEO)
        if (supportedMediaTypesFlags and 0b0100 != 0) mediaTypes.add(MediaType.AUDIO)
        if (supportedMediaTypesFlags and 0b1000 != 0) mediaTypes.add(MediaType.GIF)
        if (supportedMediaTypesFlags and 0b00010000 != 0) mediaTypes.add(MediaType.TEXT)
        if (supportedMediaTypesFlags and 0b00100000 != 0) mediaTypes.add(MediaType.PDF)
        if (supportedMediaTypesFlags and 0b01000000 != 0) mediaTypes.add(MediaType.EPUB)
        if (supportedMediaTypesFlags and 0b10000000 != 0) mediaTypes.add(MediaType.OFFICE_DOCUMENT)
        
        // Normalize path: replace backslashes with forward slashes for SMB paths
        val normalizedPath = if (type == ResourceType.SMB) {
            path.replace('\\', '/')
        } else {
            path
        }
        
        return MediaResource(
            id = id,
            name = name,
            path = normalizedPath,
            type = type,
            credentialsId = credentialsId,
            cloudProvider = cloudProvider,
            cloudFolderId = cloudFolderId,
            supportedMediaTypes = mediaTypes,
            sortMode = sortMode,
            displayMode = displayMode,
            lastViewedFile = lastViewedFile,
            lastScrollPosition = lastScrollPosition,
            fileCount = fileCount,
            subfolderCount = subfolderCount,
            lastAccessedDate = lastAccessedDate,
            slideshowInterval = slideshowInterval,
            isDestination = isDestination,
            destinationOrder = destinationOrder,
            destinationColor = destinationColor,
            isWritable = isWritable,
            isReadOnly = isReadOnly,
            isAvailable = isAvailable,
            showCommandPanel = showCommandPanel,
            createdDate = createdDate,
            lastBrowseDate = lastBrowseDate,
            lastSyncDate = lastSyncDate,
            displayOrder = displayOrder,
            scanSubdirectories = scanSubdirectories,
            disableThumbnails = disableThumbnails,
            allFiles = allFiles,
            showHiddenFiles = showHiddenFiles,
            showSubfoldersAsItems = showSubfoldersAsItems,
            rememberFileList = rememberFileList,
            accessPin = accessPin,
            comment = comment,
            accountId = accountId,
            profile = runCatching { ResourceProfile.valueOf(profile) }.getOrDefault(ResourceProfile.NONE),
            readSpeedMbps = readSpeedMbps,
            writeSpeedMbps = writeSpeedMbps,
            recommendedThreads = recommendedThreads,
            lastSpeedTestDate = lastSpeedTestDate,
            iconId = iconId,
            hostKeyFingerprint = hostKeyFingerprint,
            needsSignIn = needsSignIn, // S0200: propagate the "needs sign-in" flag from entity to domain.
            altAccessPaths = parseAltPaths(altAccessPaths), // S1006
            accessNote = accessNote, // S1014
            isHidden = isHidden, // S1009: propagate hidden flag entity -> domain
            storageVolumeId = storageVolumeId // S1378: propagate volume binding entity -> domain
        )
    }

    private fun MediaResource.toEntity(): ResourceEntity {
        var flags = 0
        if (MediaType.IMAGE in supportedMediaTypes) flags = flags or 0b0001
        if (MediaType.VIDEO in supportedMediaTypes) flags = flags or 0b0010
        if (MediaType.AUDIO in supportedMediaTypes) flags = flags or 0b0100
        if (MediaType.GIF in supportedMediaTypes) flags = flags or 0b1000
        if (MediaType.TEXT in supportedMediaTypes) flags = flags or 0b00010000
        if (MediaType.PDF in supportedMediaTypes) flags = flags or 0b00100000
        if (MediaType.EPUB in supportedMediaTypes) flags = flags or 0b01000000
        if (MediaType.OFFICE_DOCUMENT in supportedMediaTypes) flags = flags or 0b10000000
        
        return ResourceEntity(
            id = id,
            name = name,
            path = path,
            type = type,
            credentialsId = credentialsId,
            cloudProvider = cloudProvider,
            cloudFolderId = cloudFolderId,
            supportedMediaTypesFlags = flags,
            sortMode = sortMode,
            displayMode = displayMode,
            lastViewedFile = lastViewedFile,
            lastScrollPosition = lastScrollPosition,
            fileCount = fileCount,
            subfolderCount = subfolderCount,
            lastAccessedDate = lastAccessedDate,
            slideshowInterval = slideshowInterval,
            isDestination = isDestination,
            destinationOrder = destinationOrder ?: -1,
            destinationColor = destinationColor,
            isWritable = isWritable,
            isReadOnly = isReadOnly,
            isAvailable = isAvailable,
            showCommandPanel = showCommandPanel,
            createdDate = createdDate,
            lastBrowseDate = lastBrowseDate,
            lastSyncDate = lastSyncDate,
            displayOrder = displayOrder,
            scanSubdirectories = scanSubdirectories,
            disableThumbnails = disableThumbnails,
            allFiles = allFiles,  // CRITICAL FIX: Added missing allFiles field mapping
            showHiddenFiles = showHiddenFiles,  // Show hidden files (depends on allFiles)
            showSubfoldersAsItems = showSubfoldersAsItems,
            rememberFileList = rememberFileList,
            accessPin = accessPin,
            comment = comment,
            profile = profile.name,
            readSpeedMbps = readSpeedMbps,
            writeSpeedMbps = writeSpeedMbps,
            recommendedThreads = recommendedThreads,
            lastSpeedTestDate = lastSpeedTestDate,
            iconId = iconId,
            hostKeyFingerprint = hostKeyFingerprint,
            needsSignIn = needsSignIn, // S0200: propagate the "needs sign-in" flag from domain to entity.
            altAccessPaths = serializeAltPaths(altAccessPaths), // S1006
            accessNote = accessNote, // S1014
            isHidden = isHidden, // S1009: propagate hidden flag domain -> entity
            storageVolumeId = storageVolumeId // S1378: propagate volume binding domain -> entity
        )
    }

    // S1006: alternate SFTP endpoints round-trip as "host:port;host:port". Serialisation is confined to
    // these two helpers so the storage form never leaks into the domain model. A malformed stored value
    // degrades to an empty list rather than crashing the resource load.
    private fun parseAltPaths(serialized: String?): List<HostPort> {
        if (serialized.isNullOrBlank()) return emptyList()
        return serialized.split(';').mapNotNull { entry ->
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val sep = trimmed.lastIndexOf(':')
            if (sep <= 0 || sep == trimmed.length - 1) return@mapNotNull null
            val port = trimmed.substring(sep + 1).toIntOrNull() ?: return@mapNotNull null
            HostPort(trimmed.substring(0, sep), port)
        }
    }

    private fun serializeAltPaths(paths: List<HostPort>): String? {
        if (paths.isEmpty()) return null
        return paths.joinToString(";") { "${it.host}:${it.port}" }
    }
}
