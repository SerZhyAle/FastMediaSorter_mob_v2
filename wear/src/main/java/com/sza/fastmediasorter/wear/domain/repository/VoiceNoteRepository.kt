package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * S1862: the watch's own voice notes. Scope is deliberately narrow - only recordings this app made.
 * Arbitrary watch-side file operations stay with S1863 and must not grow a second transport here.
 */
interface VoiceNoteRepository {

    /** Every note, newest first. */
    fun observeNotes(): Flow<List<VoiceNote>>

    /** Notes waiting for the phone to come back, oldest first. */
    fun observePending(): Flow<List<VoiceNote>>

    /** The one-shot form of [observePending], for a callback that has no lifecycle to collect on. */
    suspend fun pendingNow(): List<VoiceNote>

    /**
     * One note by id, or null when the row is gone. A send is addressed by id rather than carrying a
     * copy of the note, so the state it writes cannot be based on a snapshot the store has moved on
     * from - and a note removed between the request and the attempt reads as null instead of sending
     * a file nobody is waiting for.
     */
    suspend fun noteById(id: Long): VoiceNote?

    /** Records a finished recording as a note. Called before any transfer is attempted (ADR-3). */
    suspend fun register(file: File, durationMillis: Long, state: VoiceNoteDeliveryState): VoiceNote

    suspend fun updateState(id: Long, state: VoiceNoteDeliveryState)

    /** S2161: records the published MediaStore address for a note. */
    suspend fun updatePublishedAddress(id: Long, publishedAddress: String)

    /** Removes the row AND the file - a note kept until deleted by hand must free its bytes then. */
    suspend fun delete(id: Long)

    /** False when the recording directory is too tight to start; asked before the microphone opens. */
    suspend fun hasRoomToRecord(): Boolean

    /** S2161: the most recently created note, or null when no note exists yet. */
    fun mostRecent(): Flow<VoiceNote?>
}
