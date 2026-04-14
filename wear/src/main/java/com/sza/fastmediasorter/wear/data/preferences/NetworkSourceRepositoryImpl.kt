package com.sza.fastmediasorter.wear.data.preferences

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.wear.data.network.ftp.FtpConnectionTest
import com.sza.fastmediasorter.wear.data.network.sftp.SftpConnectionTest
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Implementation of NetworkSourceRepository using EncryptedSharedPreferences.
 * Stores network sources as JSON with encrypted credentials.
 * 
 * Note: This class is provided via WearAppModule.provideNetworkSourceRepository
 * Do not add @Inject constructor as it would create duplicate bindings.
 */
class NetworkSourceRepositoryImpl(
    private val encryptedPrefs: SharedPreferences,
    private val smbDataSource: SmbDataSource,
    private val ftpConnectionTest: FtpConnectionTest,
    private val sftpConnectionTest: SftpConnectionTest
) : NetworkSourceRepository {
    
    private val gson = Gson()
    private val sourcesKey = "network_sources"
    private val sourcesFlow = MutableStateFlow(readSourcesFromPrefs())
    
    override suspend fun getAllSources(): List<NetworkSource> = withContext(Dispatchers.IO) {
        val sources = readSourcesFromPrefs()
        sourcesFlow.value = sources
        sources
    }

    override fun observeSources(): Flow<List<NetworkSource>> = sourcesFlow.asStateFlow()
    
    override suspend fun getSourceById(id: String): NetworkSource? = withContext(Dispatchers.IO) {
        sourcesFlow.value.firstOrNull { it.id == id } ?: readSourcesFromPrefs().firstOrNull { it.id == id }
    }
    
    override suspend fun addSource(source: NetworkSource) = withContext(Dispatchers.IO) {
        try {
            val sources = sourcesFlow.value.toMutableList()
            sources.add(source)
            saveSources(sources)
            Timber.d("Added network source: ${source.name}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to add network source")
            throw e
        }
    }
    
    override suspend fun updateSource(source: NetworkSource) = withContext(Dispatchers.IO) {
        try {
            val sources = sourcesFlow.value.toMutableList()
            val index = sources.indexOfFirst { it.id == source.id }
            if (index != -1) {
                sources[index] = source
                saveSources(sources)
                Timber.d("Updated network source: ${source.name}")
            } else {
                throw IllegalArgumentException("Source not found: ${source.id}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update network source")
            throw e
        }
    }
    
    override suspend fun upsertSource(source: NetworkSource) = withContext(Dispatchers.IO) {
        try {
            val sources = sourcesFlow.value.toMutableList()
            val index = sources.indexOfFirst { existing ->
                existing.type == source.type &&
                existing.server == source.server &&
                existing.port == source.port &&
                existing.shareName == source.shareName
            }
            if (index != -1) {
                sources[index] = source.copy(id = sources[index].id) // preserve original id
                Timber.d("upsertSource: updated ${source.name}")
            } else {
                sources.add(source)
                Timber.d("upsertSource: added ${source.name}")
            }
            saveSources(sources)
        } catch (e: Exception) {
            Timber.e(e, "Failed to upsert network source")
            throw e
        }
    }

    override suspend fun deleteSource(id: String) = withContext(Dispatchers.IO) {
        try {
            val sources = sourcesFlow.value.toMutableList()
            sources.removeAll { it.id == id }
            saveSources(sources)
            Timber.d("Deleted network source: $id")
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete network source")
            throw e
        }
    }
    
    override suspend fun testConnection(source: NetworkSource): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            when (source.type) {
                NetworkSourceType.SMB -> {
                    val result = smbDataSource.connect(source)
                    if (result.isSuccess) {
                        val isConnected = smbDataSource.isConnected()
                        smbDataSource.disconnect()
                        Result.success(isConnected)
                    } else {
                        Result.failure(result.exceptionOrNull() ?: Exception("Connection failed"))
                    }
                }
                NetworkSourceType.FTP -> ftpConnectionTest.testFtp(source)
                NetworkSourceType.SFTP -> sftpConnectionTest.testSftp(source)
                else -> Result.failure(UnsupportedOperationException("Source type not supported: ${source.type}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Connection test failed")
            Result.failure(e)
        }
    }
    
    private fun saveSources(sources: List<NetworkSource>) {
        val json = gson.toJson(sources)
        encryptedPrefs.edit().putString(sourcesKey, json).apply()
        sourcesFlow.value = sources
    }

    private fun readSourcesFromPrefs(): List<NetworkSource> {
        return try {
            val json = encryptedPrefs.getString(sourcesKey, null) ?: return emptyList()
            val type = TypeToken.getParameterized(List::class.java, NetworkSource::class.java).type
            gson.fromJson<List<NetworkSource>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to load network sources")
            emptyList()
        }
    }
}
