package com.sza.fastmediasorter.domain.model.notes

/** What one notes sync ended with (DEVICE-EXCHANGE 1.13 items FI to FK). */
sealed interface NotesSyncOutcome {
    /** The option is off: no entry was picked, and no notes frame was sent. */
    data object Off : NotesSyncOutcome
    data object NotOffered : NotesSyncOutcome

    /** The server lacks `sealed`; shown `server-no-e2e` (item DR), and nothing was sent in clear. */
    data object ServerNoE2e : NotesSyncOutcome
    data object Guest : NotesSyncOutcome
    data object NoRecipient : NotesSyncOutcome
    data class Refused(val reason: String) : NotesSyncOutcome

    /** [tooLarge] names the note files above the item FH limit, which stay on this device. */
    data class Synced(
        val host: String,
        val received: Int,
        val sent: Int,
        val conflicts: Int,
        val skipped: Int,
        val refused: Int,
        val tooLarge: List<String>,
        val limitReached: Boolean,
    ) : NotesSyncOutcome
}
