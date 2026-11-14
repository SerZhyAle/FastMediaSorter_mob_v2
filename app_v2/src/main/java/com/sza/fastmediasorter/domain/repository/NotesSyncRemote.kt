package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry

/** What one stored note record holds as this device can use it (DEVICE-EXCHANGE 1.13 items FG and FH). */
sealed interface RemoteNoteContent {
    /** [json] is the UTF-8 item FH payload, opened from the record's box. */
    class Payload(val json: ByteArray) : RemoteNoteContent
    data object Deleted : RemoteNoteContent

    /** The box did not open for this device; the local note is left untouched. */
    data object Unopened : RemoteNoteContent

    /** `format-unsupported`: the local note is neither changed nor deleted. */
    data object Unsupported : RemoteNoteContent
}

data class RemoteNote(val noteId: String, val revision: Long, val content: RemoteNoteContent)

sealed interface NotesListOutcome {
    /** [full] means a synced note found in no record was deleted on the server (item FI). */
    data class Listed(val notesRevision: Long, val full: Boolean, val notes: List<RemoteNote>) : NotesListOutcome
    data class Refused(val reason: String) : NotesListOutcome
    data object NotOffered : NotesListOutcome
    data object NotSealed : NotesListOutcome
    data object Guest : NotesListOutcome
}

sealed interface NotesPutOutcome {
    data class Stored(val revision: Long) : NotesPutOutcome

    /** `note-conflict` (item FJ): nothing was stored; [current] is what the server holds. */
    data class Conflict(val current: RemoteNote) : NotesPutOutcome
    data class Refused(val reason: String) : NotesPutOutcome
    data object TooLarge : NotesPutOutcome
    data object NoRecipient : NotesPutOutcome
    data object NotOffered : NotesPutOutcome
    data object NotSealed : NotesPutOutcome
    data object Guest : NotesPutOutcome
}

/** The account's notes store as the sync use case sees it, so the sync is testable over a fake server. */
interface NotesSyncRemote {

    /** The one entry the person picked for the notes sync; null while the option is off (item FK). */
    suspend fun entry(): DeviceExchangeAccountEntry?

    suspend fun list(entry: DeviceExchangeAccountEntry, since: Long): NotesListOutcome

    /** A put of the item FH [payload] over [baseRevision]; a null [payload] deletes the note. */
    suspend fun put(
        entry: DeviceExchangeAccountEntry,
        noteId: String,
        baseRevision: Long,
        payload: ByteArray?,
    ): NotesPutOutcome
}
