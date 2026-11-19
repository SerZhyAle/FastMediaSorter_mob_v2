package com.sza.fastmediasorter.data.repository

import androidx.room.withTransaction
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointCodec
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointCodec.Protection
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.StreamSourceDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4675: the sealing of a saved broadcast's relay keys and tunnel pins, apart from the catalog repository it guards.
 * Every call here reaches the Android Keystore, so each one runs off the main thread.
 */
@Singleton
class BroadcastEndpointSecretsRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: StreamSourceDao,
) {

    /** What one [protectStored] run did; counts only, a row's text is never reported. */
    data class Migration(val migrated: Int, val raced: Int, val failed: Int)

    /**
     * Moves every saved row that still holds a clear relay key or tunnel pin behind the device's box, without
     * waiting for the row to be played. Every list is sealed and opened again before anything is written; each row
     * is then replaced by one compare-and-set against the exact text that was read, all in one transaction so the
     * list re-emits once. A failure or a concurrent refresh leaves that row as it was, and a run that is interrupted
     * or loses a race is finished by the next one.
     */
    suspend fun protectStored(): Migration {
        val sealed = withContext(Dispatchers.IO) {
            dao.rowsWithPossibleClearEndpointSecrets().map { row ->
                row to BroadcastEndpointCodec.protect(row.broadcastEndpoints)
            }
        }
        var migrated = 0
        var raced = 0
        db.withTransaction {
            for ((row, outcome) in sealed) {
                if (outcome !is Protection.Protected) continue
                val changed = dao.replaceBroadcastEndpointsIf(row.id, row.broadcastEndpoints, outcome.value)
                if (changed > 0) migrated++ else raced++
            }
        }
        return Migration(migrated, raced, failed = sealed.count { it.second == Protection.Failed })
    }

    /** The value to write for [endpoints]; a caller that writes it later hands the sealed text back as it is. */
    suspend fun seal(endpoints: String?): Protection =
        withContext(Dispatchers.IO) { BroadcastEndpointCodec.protect(endpoints) }

    /** Whether two stored lists hold the same endpoints, sealed secrets compared by what they hold. */
    suspend fun isSame(first: String?, second: String?): Boolean =
        withContext(Dispatchers.IO) { BroadcastEndpointCodec.sameEndpoints(first, second) }
}
