package com.sza.fastmediasorter.wear.data.preferences

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.wear.data.network.ftp.FtpConnectionTest
import com.sza.fastmediasorter.wear.data.network.sftp.SftpConnectionTest
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceMerge
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearSourceTombstonePayload
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.util.errorUnlessCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val tombstonesKey = "network_source_tombstones"
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

    // S2502: this method and [updateSource] are the user-edit path, so they stamp the moment the write
    // happens. `upsertSource` deliberately does not - it is the import path and must carry the stamp
    // the merge resolved, or every imported record would look freshly edited on the next exchange.
    override suspend fun addSource(source: NetworkSource) = withContext(Dispatchers.IO) {
        try {
            val sources = sourcesFlow.value.toMutableList()
            sources.add(source.copy(lastEditedAt = System.currentTimeMillis()))
            saveSources(sources)
            Timber.d("Added network source: ${source.name}")
        } catch (e: Exception) {
            e.errorUnlessCancellation("Failed to add network source")
            throw e
        }
    }

    override suspend fun updateSource(source: NetworkSource) = withContext(Dispatchers.IO) {
        try {
            val sources = sourcesFlow.value.toMutableList()
            val index = sources.indexOfFirst { it.id == source.id }
            if (index != -1) {
                // S2502: see the note on addSource - the user-edit path stamps, the import path does not.
                sources[index] = source.copy(lastEditedAt = System.currentTimeMillis())
                Timber.d("S2502: watch edit stamped for source ${source.id}")
                saveSources(sources)
                Timber.d("Updated network source: ${source.name}")
            } else {
                throw IllegalArgumentException("Source not found: ${source.id}")
            }
        } catch (e: Exception) {
            e.errorUnlessCancellation("Failed to update network source")
            throw e
        }
    }

    override suspend fun upsertSource(source: NetworkSource) = withContext(Dispatchers.IO) {
        try {
            val sources = sourcesFlow.value.toMutableList()
            val index = NetworkSourceMerge.indexOfMatch(sources, source)
            if (index != -1) {
                // S1734: the incoming id is kept, not replaced by the stored one. Preserving the old
                // id was what made the discrepancy permanent - a row matched by the share tuple kept
                // an id the phone never sends again, so every later sync counted it as new. Adopting
                // the incoming id lets the next sync match by id and the counts converge.
                sources[index] = source
                Timber.d("upsertSource: updated ${source.name}")
            } else {
                sources.add(source)
                Timber.d("upsertSource: added ${source.name}")
            }
            saveSources(sources)
        } catch (e: Exception) {
            e.errorUnlessCancellation("Failed to upsert network source")
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
            e.errorUnlessCancellation("Failed to delete network source")
            throw e
        }
    }

    override suspend fun deleteSourceWithTombstone(id: String, deletedAt: Long) {
        // S2507: the deletion event is stored before the ordinary record goes, so an interruption
        // between the two leaves evidence of the delete rather than a silently resurrectable source.
        recordTombstone(WearSourceTombstonePayload(id = id, deletedAt = deletedAt))
        deleteSource(id)
    }

    override suspend fun getTombstones(): List<WearSourceTombstonePayload> = withContext(Dispatchers.IO) {
        readTombstonesFromPrefs()
    }

    override suspend fun recordTombstone(tombstone: WearSourceTombstonePayload) = withContext(Dispatchers.IO) {
        val updated = readTombstonesFromPrefs().filterNot { it.id == tombstone.id } + tombstone
        saveTombstones(updated)
    }

    override suspend fun removeTombstone(id: String) = withContext(Dispatchers.IO) {
        val current = readTombstonesFromPrefs()
        val updated = current.filterNot { it.id == id }
        if (updated.size != current.size) {
            saveTombstones(updated)
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
            e.errorUnlessCancellation("Connection test failed")
            Result.failure(e)
        }
    }

    private fun saveSources(sources: List<NetworkSource>) {
        val json = gson.toJson(sources)
        encryptedPrefs.edit().putString(sourcesKey, json).apply()
        sourcesFlow.value = sources
    }

    private fun saveTombstones(tombstones: List<WearSourceTombstonePayload>) {
        encryptedPrefs.edit().putString(tombstonesKey, gson.toJson(tombstones)).apply()
    }

    private fun readTombstonesFromPrefs(): List<WearSourceTombstonePayload> {
        return try {
            val json = encryptedPrefs.getString(tombstonesKey, null) ?: return emptyList()
            val type = TypeToken.getParameterized(List::class.java, WearSourceTombstonePayload::class.java).type
            gson.fromJson<List<WearSourceTombstonePayload>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to load network source tombstones")
            emptyList()
        }
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
