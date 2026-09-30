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
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    // S3368: Lazy, so constructing this repository does not load the SMB, FTP and SFTP protocol
    // stacks. The repository is built on paths as early as the cold-start injection and as ordinary
    // as the home screen's last-used row, while the three protocol objects below have exactly one
    // reader - [testConnection], reached from the network-sources screen long after start.
    // S3830: SMB is a factory, not the shared singleton. A test connects and disconnects, and connect
    // begins by closing the current link, so testing on the singleton cut off the track or the browse
    // reading through it. Each test owns a throwaway instance instead.
    private val newSmbProbe: () -> SmbDataSource,
    private val ftpConnectionTest: Lazy<FtpConnectionTest>,
    private val sftpConnectionTest: Lazy<SftpConnectionTest>
) : NetworkSourceRepository {

    private val gson = Gson()
    private val sourcesKey = "network_sources"
    private val tombstonesKey = "network_source_tombstones"
    private val sourcesFlow = MutableStateFlow(readSourcesFromPrefs())

    // S3832: every write reads the whole list or tombstone set, edits it and writes it all back, so
    // two unserialized writers (a phone sync importing while the wearer edits) lose one of the edits.
    private val writeTurn = Mutex()

    private suspend fun <T> storeWrite(block: () -> T): T = withContext(Dispatchers.IO) {
        writeTurn.withLock { block() }
    }

    // Under the write turn too: a refresh read before a save and published after it would roll the
    // flow back to the list the save replaced.
    override suspend fun getAllSources(): List<NetworkSource> = storeWrite {
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
    override suspend fun addSource(source: NetworkSource) = storeWrite {
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

    override suspend fun updateSource(source: NetworkSource) = storeWrite {
        try {
            val sources = sourcesFlow.value.toMutableList()
            val index = sources.indexOfFirst { it.id == source.id }
            if (index != -1) {
                // S2502: see the note on addSource - the user-edit path stamps, the import path does not.
                sources[index] = source.copy(lastEditedAt = System.currentTimeMillis())
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

    override suspend fun upsertSource(source: NetworkSource) = storeWrite {
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

    override suspend fun deleteSource(id: String) = storeWrite {
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

    override suspend fun recordTombstone(tombstone: WearSourceTombstonePayload) = storeWrite {
        val updated = readTombstonesFromPrefs().filterNot { it.id == tombstone.id } + tombstone
        saveTombstones(updated)
    }

    override suspend fun removeTombstone(id: String) = storeWrite {
        val current = readTombstonesFromPrefs()
        val updated = current.filterNot { it.id == id }
        if (updated.size != current.size) {
            saveTombstones(updated)
        }
    }

    override suspend fun testConnection(source: NetworkSource): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            when (source.type) {
                NetworkSourceType.SMB -> testSmb(source)
                NetworkSourceType.FTP -> ftpConnectionTest.get().testFtp(source)
                NetworkSourceType.SFTP -> sftpConnectionTest.get().testSftp(source)
            }
        } catch (e: Exception) {
            e.errorUnlessCancellation("Connection test failed")
            Result.failure(e)
        }
    }

    private suspend fun testSmb(source: NetworkSource): Result<Boolean> {
        val probe = newSmbProbe()
        return try {
            probe.connect(source).map { probe.isConnected() }
        } finally {
            // A cancelled test still owns an open socket; the disconnect must not be cancelled with it.
            withContext(NonCancellable) { probe.disconnect() }
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
