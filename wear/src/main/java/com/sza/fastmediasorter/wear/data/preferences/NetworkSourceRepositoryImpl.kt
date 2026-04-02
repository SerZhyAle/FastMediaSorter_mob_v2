package com.sza.fastmediasorter.wear.data.preferences

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
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
    private val smbDataSource: SmbDataSource
) : NetworkSourceRepository {
    
    private val gson = Gson()
    private val sourcesKey = "network_sources"
    
    override suspend fun getAllSources(): List<NetworkSource> = withContext(Dispatchers.IO) {
        try {
            val json = encryptedPrefs.getString(sourcesKey, null) ?: return@withContext emptyList()
            val type = TypeToken.getParameterized(List::class.java, NetworkSource::class.java).type
            gson.fromJson<List<NetworkSource>>(json, type)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load network sources")
            emptyList()
        }
    }
    
    override suspend fun getSourceById(id: String): NetworkSource? = withContext(Dispatchers.IO) {
        getAllSources().firstOrNull { it.id == id }
    }
    
    override suspend fun addSource(source: NetworkSource) = withContext(Dispatchers.IO) {
        try {
            val sources = getAllSources().toMutableList()
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
            val sources = getAllSources().toMutableList()
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
    
    override suspend fun deleteSource(id: String) = withContext(Dispatchers.IO) {
        try {
            val sources = getAllSources().toMutableList()
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
    }
}
