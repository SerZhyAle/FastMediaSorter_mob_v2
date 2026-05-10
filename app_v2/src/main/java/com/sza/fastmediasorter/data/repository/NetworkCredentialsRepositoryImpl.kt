package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import java.io.File
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.sza.fastmediasorter.core.di.ApplicationScope
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

@Singleton
class NetworkCredentialsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: NetworkCredentialsDao,
    private val resourceDao: ResourceDao,
    private val settingsRepository: SettingsRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope  // ML-009: Application-scoped coroutine for background tasks
) : NetworkCredentialsRepository {

    // P0-4: Session-level cache for share-specific SMB credential lookups.
    // Eliminates repeated DAO queries for shares that have no specific credentials (NULL result).
    // Key: "server:shareName", Value: Optional.empty() for confirmed-null, Optional.of(entity) for found.
    // Invalidated on any credential insert/update/delete.
    private val shareCredentialCache = ConcurrentHashMap<String, Optional<NetworkCredentialsEntity>>()

    init {
        if (BuildConfig.DEBUG) {
            Timber.i("TEST_CREDS: Initializing NetworkCredentialsRepositoryImpl")
            applicationScope.launch(Dispatchers.IO) {  // Use injected application scope (ML-009)
                try {
                    Timber.i("TEST_CREDS: Starting loadTestCredentials")
                    loadTestCredentials()
                } catch (e: Exception) {
                    Timber.e(e, "TEST_CREDS: Failed to load test credentials")
                }
            }
        }
    }

    private suspend fun loadTestCredentials() {
        // Use app-specific storage to avoid permission issues
        // Path: /sdcard/Android/data/com.sza.fastmediasorter/files/test_media/test_credentials.json
        val testMediaDir = File(context.getExternalFilesDir(null), "test_media")
        val file = File(testMediaDir, "test_credentials.json")
        
        if (file.exists()) {
            Timber.i("TEST_CREDS: File found at ${file.absolutePath}, size=${file.length()}")
            try {
                val json = file.readText()
                val config = Gson().fromJson(json, TestCredentialsConfig::class.java)
                
                if (config.credentials.isNotEmpty()) {
                    Timber.d("TEST_CREDS: Loading ${config.credentials.size} test credentials from ${file.absolutePath}")
                    
                    for (cred in config.credentials) {
                        Timber.d("TEST_CREDS: Processing credential type=${cred.type} server=${cred.server}")
                        
                        // 1. Handle Credential
                        // Encrypt password
                        val encryptedPass = CryptoHelper.encrypt(cred.password) ?: ""
                        
                        var existingCred = if (cred.type == "SMB") {
                            dao.getByServerAndShare(cred.server, cred.shareName ?: "")
                        } else {
                            dao.getByTypeServerAndPort(cred.type, cred.server, cred.port ?: (if(cred.type=="SFTP") 22 else 21))
                        }
                        
                        val activeCredentialId = if (existingCred == null) {
                            val newEntity = NetworkCredentialsEntity(
                                credentialId = java.util.UUID.randomUUID().toString(),
                                type = cred.type,
                                server = cred.server,
                                port = cred.port ?: (if (cred.type == "SMB") 445 else if (cred.type == "SFTP") 22 else 21),
                                username = cred.username,
                                encryptedPassword = encryptedPass,
                                shareName = cred.shareName ?: "",
                                domain = cred.domain ?: ""
                            )
                            dao.insert(newEntity)
                            Timber.d("Inserted test credential for ${cred.server} (${cred.type})")
                            newEntity.credentialId
                        } else {
                            Timber.d("Credential for ${cred.server} already exists, using existing.")
                            existingCred.credentialId
                        }
                        
                        // 2. Handle Resource (Create if missing, even if credential existed)
                        val resourceType = when(cred.type.uppercase()) {
                            "SMB" -> ResourceType.SMB
                            "SFTP" -> ResourceType.SFTP
                            "FTP" -> ResourceType.FTP
                            else -> ResourceType.SMB
                        }
                        
                        val resourcePath = if (resourceType == ResourceType.SMB) {
                            "smb://${cred.server}/${cred.shareName ?: ""}"
                        } else {
                            cred.folder ?: "/"
                        }
                        
                        val resourceName = if (resourceType == ResourceType.SMB) {
                            cred.shareName ?: cred.server
                        } else {
                            cred.folder?.takeIf { it.isNotBlank() && it != "/" } ?: "${cred.server} (${cred.type})"
                        }

                        // Check if resource exists using a simple check (we'll fetch by raw query or just try insert if we had a unique constraint, but we don't on name/path combo necessarily)
                        // But wait, ResourceDao doesn't have an easy "getByNameAndPath".
                        // However, for testing, we can just fetch all and check in memory if list is small, or just Insert with IGNORE strategy if we could.
                        // Our DAO has @Insert(onConflict = OnConflictStrategy.REPLACE). REPLACE will overwrite config like sort order.
                        // We probably want to SKIP if it exists.
                        // Let's assume if the credential was new, we definitely need the resource.
                        // If credential existed, maybe resource exists. 
                        // Let's verify via a query.
                        
                        val allResources = resourceDao.getAllResourcesSync()
                        val existingResource = allResources.find { it.name == resourceName && it.type == resourceType }
                        
                        if (existingResource == null) {
                            val newResource = ResourceEntity(
                                name = resourceName,
                                path = resourcePath,
                                type = resourceType,
                                credentialsId = activeCredentialId,
                                isAvailable = true,
                                displayOrder = 100 // Put at end
                            )
                            resourceDao.insert(newResource)
                            Timber.d("Inserted test resource: $resourceName")
                        } else {
                             // Check if path format needs update (e.g. was "share" now "smb://server/share")
                             if (existingResource.path != resourcePath) {
                                 Timber.d("Updating resource path for $resourceName from '${existingResource.path}' to '$resourcePath'")
                                 resourceDao.update(existingResource.copy(path = resourcePath))
                             } else {
                                 Timber.d("Resource $resourceName already exists and correct, skipping.")
                             }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "TEST_CREDS: Error parsing test_credentials.json")
            }
        } else {
            // S0055-C: absent on end-user devices is normal \u2014 downgraded from w to d
            Timber.d("TEST_CREDS: Test credentials file not found at ${file.absolutePath}")
        }
    }

    override suspend fun insert(credentials: NetworkCredentialsEntity): Long {
        warnIfEmptyShareName(credentials, op = "insert")
        shareCredentialCache.clear()
        return dao.insert(credentials)
    }

    override suspend fun getById(id: Long): NetworkCredentialsEntity? {
        val entity = dao.getById(id)
        return applyDefaultCredentialsIfNeeded(entity)
    }

    override suspend fun getByCredentialId(credentialId: String): NetworkCredentialsEntity? {
        val entity = dao.getCredentialsById(credentialId)
        return applyDefaultCredentialsIfNeeded(entity)
    }

    override suspend fun getByTypeServerAndPort(
        type: String,
        server: String,
        port: Int
    ): NetworkCredentialsEntity? {
        val entity = dao.getByTypeServerAndPort(type, server, port)
        return applyDefaultCredentialsIfNeeded(entity)
    }

    override suspend fun getByServerAndShare(server: String, shareName: String): NetworkCredentialsEntity? {
        val cacheKey = "$server:$shareName"
        val cached = shareCredentialCache[cacheKey]
        if (cached != null) {
            val result = if (cached.isPresent) cached.get() else null
            Timber.v("NetworkCredentialsRepository: getByServerAndShare cache hit for '$cacheKey': ${if (result != null) "FOUND" else "NULL"}")
            return applyDefaultCredentialsIfNeeded(result)
        }

        Timber.d("NetworkCredentialsRepository: getByServerAndShare(server='$server', share='$shareName')")
        val entity = dao.getByServerAndShare(server, shareName)
        Timber.d("NetworkCredentialsRepository: DAO returned ${if (entity != null) "FOUND (id=${entity.credentialId})" else "NULL"}")

        shareCredentialCache[cacheKey] = if (entity != null) Optional.of(entity) else Optional.empty()
        return applyDefaultCredentialsIfNeeded(entity)
    }

    override suspend fun getCredentialsByHost(host: String): NetworkCredentialsEntity? {
        val entity = dao.getCredentialsByHost(host)
        return applyDefaultCredentialsIfNeeded(entity)
    }

    override suspend fun getByTypeAndAccountId(type: String, accountId: String): NetworkCredentialsEntity? {
        val entity = dao.getByTypeAndAccountId(type, accountId)
        return applyDefaultCredentialsIfNeeded(entity)
    }

    override suspend fun update(credentials: NetworkCredentialsEntity) {
        warnIfEmptyShareName(credentials, op = "update")
        shareCredentialCache.clear()
        dao.update(credentials)
    }

    override suspend fun delete(credentials: NetworkCredentialsEntity) {
        shareCredentialCache.clear()
        // DAO doesn't have delete by entity, use deleteByCredentialId
        dao.deleteByCredentialId(credentials.credentialId)
    }

    override fun getAllCredentials(): kotlinx.coroutines.flow.Flow<List<NetworkCredentialsEntity>> {
        return dao.getAllCredentials()
    }

    override suspend fun getOrphanedCredentials(): List<NetworkCredentialsEntity> {
        return dao.getOrphanedCredentials()
    }

    // S0064 -----------------------------------------------------------------------

    override suspend fun getManualShareNamesForServer(server: String, port: Int): List<String> {
        val credentials = dao.getAllByTypeServerAndPort("SMB", server, port)
        // Collect all previously saved share names + any names from the manualShareNames fields.
        // shareName on each entity is already a known-good name for this server.
        val names = mutableSetOf<String>()
        for (cred in credentials) {
            cred.shareName?.takeIf { it.isNotBlank() }?.let { names.add(it) }
            cred.manualShareNames.split('|').forEach { n -> if (n.isNotBlank()) names.add(n) }
        }
        return names.toList().sorted()
    }

    override suspend fun addManualShareName(server: String, port: Int, shareName: String) {
        if (shareName.isBlank()) return
        val credentials = dao.getAllByTypeServerAndPort("SMB", server, port)
        if (credentials.isEmpty()) {
            // No credential entry for this server yet — nothing to attach the name to.
            // The name will be persisted once the user completes an add (via saveCredentials).
            return
        }
        // Append to the first credential for this server; all others will see it via the next
        // call to getManualShareNamesForServer which scans all entries.
        val target = credentials.first()
        val existing = target.manualShareNames.split('|').filter { it.isNotBlank() }.toMutableSet()
        if (!existing.contains(shareName)) {
            existing.add(shareName)
            shareCredentialCache.clear()
            dao.update(target.copy(manualShareNames = existing.joinToString("|")))
        }
    }

    // -----------------------------------------------------------------------------

    /**
     * S0139: defense-in-depth check. SMB credentials must carry a non-empty `shareName`;
     * otherwise the runtime self-heal in [com.sza.fastmediasorter.data.repository.ResourceRepositoryImpl.testSmbConnection]
     * fires a `W` log on every connection test. Non-throwing — some import paths legitimately
     * receive credentials without share metadata, so we attribute the source via Timber rather
     * than blocking the write.
     */
    private fun warnIfEmptyShareName(entity: NetworkCredentialsEntity, op: String) {
        if (entity.type.equals("SMB", ignoreCase = true) && entity.shareName.isNullOrEmpty()) {
            Timber.w(
                "S0139: SMB credential persisted with empty shareName " +
                    "(op=$op, server='${entity.server}', credentialId='${entity.credentialId}')"
            )
        }
    }

    private suspend fun applyDefaultCredentialsIfNeeded(entity: NetworkCredentialsEntity?): NetworkCredentialsEntity? {
        if (entity == null) return null

        // If username and password are provided, use them
        if (entity.username.isNotEmpty() && entity.encryptedPassword.isNotEmpty()) {
            return entity
        }

        // If username or password is missing, try to use default credentials from settings
        val settings = settingsRepository.getSettings().first()
        val defaultUser = settings.defaultUser
        val defaultPassword = settings.defaultPassword

        // If default credentials are also empty, return original entity
        if (defaultUser.isEmpty() && defaultPassword.isEmpty()) {
            return entity
        }

        var newUsername = entity.username
        var newEncryptedPassword = entity.encryptedPassword

        // Use default username if entity's username is empty
        if (newUsername.isEmpty()) {
            newUsername = defaultUser
        }

        // Use default password if entity's password is empty
        // Note: entity.encryptedPassword stores the encrypted password.
        // If it's empty, it means no password was set.
        if (newEncryptedPassword.isEmpty() && defaultPassword.isNotEmpty()) {
            newEncryptedPassword = com.sza.fastmediasorter.data.local.db.CryptoHelper.encrypt(defaultPassword) ?: ""
        }

        return entity.copy(
            username = newUsername,
            encryptedPassword = newEncryptedPassword
        )
    }
}
