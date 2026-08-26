package com.sza.fastmediasorter.wear.data.repository

import com.sza.fastmediasorter.wear.data.db.VoiceNoteDao
import com.sza.fastmediasorter.wear.data.db.VoiceNoteEntity
import com.sza.fastmediasorter.wear.data.db.toDomain
import com.sza.fastmediasorter.wear.data.recorder.VoiceNoteFileFactory
import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState
import com.sza.fastmediasorter.wear.domain.repository.VoiceNoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Section 7 calls a full watch filling a high-probability risk: recording has to be refused BEFORE
 * the microphone opens, not truncated once the disk runs out. The headroom equals the bridge ceiling
 * of S1861, so anything that can be started can also still be sent.
 */
private const val MIN_FREE_BYTES_TO_RECORD = 32L * 1024L * 1024L

/**
 * S1862: the store behind the recorder's note list.
 *
 * Every method hops to [Dispatchers.IO]; the Flow queries are Room's own, which are already
 * main-safe, and only the mapping to the domain shape runs on the collector.
 */
@Singleton
class VoiceNoteRepositoryImpl @Inject constructor(
    private val dao: VoiceNoteDao,
    private val fileFactory: VoiceNoteFileFactory
) : VoiceNoteRepository {

    override fun observeNotes(): Flow<List<VoiceNote>> =
        dao.observeAll().map { entities -> entities.map(VoiceNoteEntity::toDomain) }

    override fun observePending(): Flow<List<VoiceNote>> =
        dao.observePending(VoiceNoteDeliveryState.PENDING.name)
            .map { entities -> entities.map(VoiceNoteEntity::toDomain) }

    override suspend fun pendingNow(): List<VoiceNote> = withContext(Dispatchers.IO) {
        dao.getPendingOnce(VoiceNoteDeliveryState.PENDING.name).map(VoiceNoteEntity::toDomain)
    }

    override suspend fun noteById(id: Long): VoiceNote? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun register(
        file: File,
        durationMillis: Long,
        state: VoiceNoteDeliveryState
    ): VoiceNote = withContext(Dispatchers.IO) {
        val entity = VoiceNoteEntity(
            fileName = file.name,
            absolutePath = file.absolutePath,
            createdAtMillis = System.currentTimeMillis(),
            durationMillis = durationMillis,
            sizeBytes = file.length(),
            deliveryState = state.name
        )
        val id = dao.insert(entity)
        entity.copy(id = id).toDomain()
    }

    override suspend fun updateState(id: Long, state: VoiceNoteDeliveryState) = withContext(Dispatchers.IO) {
        dao.updateDeliveryState(id, state.name)
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        // The row goes only after the file: a row without a file is a broken list entry, while a file
        // without a row is an orphan nobody can reach. If the process dies between the two, the
        // second failure mode is the recoverable one.
        val entity = dao.getById(id)
        if (entity != null) {
            deleteFileOf(entity)
        }
        dao.deleteById(id)
    }

    override suspend fun hasRoomToRecord(): Boolean = withContext(Dispatchers.IO) {
        val free = fileFactory.directory().usableSpace
        val roomy = free >= MIN_FREE_BYTES_TO_RECORD
        if (!roomy) {
            Timber.i("Refusing to start a recording: %d bytes free on the watch", free)
        }
        roomy
    }

    private fun deleteFileOf(entity: VoiceNoteEntity) {
        val file = File(entity.absolutePath)
        if (file.exists() && !file.delete()) {
            // Nothing the user can do about it and nothing to roll back - the row still goes, so the
            // note leaves the list either way. Worth knowing about only when the disk misbehaves.
            Timber.w("Failed to delete the voice-note file %s", entity.fileName)
        }
    }
}
