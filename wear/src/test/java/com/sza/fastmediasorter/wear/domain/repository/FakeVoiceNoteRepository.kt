package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * S1862: an in-memory note store for the JVM tests of the send and drain use-cases.
 *
 * Shared by both because they assert on the same thing from two sides - which delivery state a
 * transport outcome writes. A copy per test file would let the two drift apart and hide exactly the
 * mapping bug the tests exist to catch.
 */
class FakeVoiceNoteRepository(initial: List<VoiceNote> = emptyList()) : VoiceNoteRepository {

    val notes: MutableMap<Long, VoiceNote> = initial.associateBy { it.id }.toMutableMap()

    /** Every state write in order, so a test can assert that a note was written once and not twice. */
    val stateWrites: MutableList<Pair<Long, VoiceNoteDeliveryState>> = mutableListOf()

    val deletedIds: MutableList<Long> = mutableListOf()

    var roomToRecord: Boolean = true

    /** S2495: set to stand for a private file that will not move, so a rename fails on that half. */
    var renameRefused: Boolean = false

    override fun observeNotes(): Flow<List<VoiceNote>> = MutableStateFlow(notes.values.toList())

    override fun observePending(): Flow<List<VoiceNote>> = MutableStateFlow(pendingOrdered())

    override suspend fun pendingNow(): List<VoiceNote> = pendingOrdered()

    override suspend fun noteById(id: Long): VoiceNote? = notes[id]

    override suspend fun register(
        file: File,
        durationMillis: Long,
        state: VoiceNoteDeliveryState
    ): VoiceNote {
        val id = (notes.keys.maxOrNull() ?: 0L) + 1L
        val note = VoiceNote(
            id = id,
            fileName = file.name,
            absolutePath = file.absolutePath,
            createdAtMillis = id,
            durationMillis = durationMillis,
            sizeBytes = file.length(),
            deliveryState = state
        )
        notes[id] = note
        return note
    }

    override suspend fun updateState(id: Long, state: VoiceNoteDeliveryState) {
        stateWrites += id to state
        notes[id]?.let { notes[id] = it.copy(deliveryState = state) }
    }

    /**
     * S2495: renames in memory the way the real one renames on disk - both name fields together, or
     * neither. [renameRefused] is what a test sets to stand for a file that would not move.
     */
    override suspend fun rename(id: Long, newName: String): VoiceNote? {
        val note = notes[id]?.takeUnless { renameRefused } ?: return null
        val renamed = note.copy(
            fileName = newName,
            absolutePath = note.absolutePath.substringBeforeLast('/') + "/" + newName
        )
        notes[id] = renamed
        return renamed
    }

    override suspend fun updatePublishedAddress(id: Long, publishedAddress: String) {
        notes[id]?.let { notes[id] = it.copy(publishedAddress = publishedAddress) }
    }

    override suspend fun delete(id: Long) {
        deletedIds += id
        notes.remove(id)
    }

    override suspend fun hasRoomToRecord(): Boolean = roomToRecord

    override fun mostRecent(): Flow<VoiceNote?> =
        MutableStateFlow(notes.values.maxByOrNull { it.createdAtMillis })

    /** Oldest first, mirroring the DAO query the drain relies on so ordering bugs stay visible here. */
    private fun pendingOrdered(): List<VoiceNote> = notes.values
        .filter { it.deliveryState == VoiceNoteDeliveryState.PENDING }
        .sortedBy { it.createdAtMillis }
}
