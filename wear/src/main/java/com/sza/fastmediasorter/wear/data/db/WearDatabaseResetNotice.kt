package com.sza.fastmediasorter.wear.data.db

import android.content.Context
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import timber.log.Timber

private const val PREFS_NAME = "wear_database_reset_notice"
private const val KEY_PENDING = "pending"
private const val KEY_REASON = "reason"
private const val KEY_RECOVERED_NOTES = "recovered_notes"

/** Shown when the stored reason is unreadable - the notice still has to say something. */
private const val UNKNOWN_REASON = "unknown"

private const val MAX_CAUSE_DEPTH = 8

/** Substrings of Room's own open-time errors: a missing or broken migration and an identity-hash mismatch. */
private val ROOM_SCHEMA_MARKERS = listOf("migration", "data integrity")

/**
 * S2356: remembers that the watch had to recreate its note database, so the note list can say so
 * once rather than letting every delivery badge silently read as reset.
 *
 * Mirrors the phone's `DatabaseResetNotice` with one deliberate omission: this one keeps no copy of
 * the old database and reports no path to one. ADR-1 rejects copying it aside because a watch owner
 * cannot reach app-private storage - there is no file manager on the watch and the media index does
 * not scan private directories - so the copy would cost space and give nothing back. The recordings
 * themselves are what survives, and the rebuild reads them straight off disk.
 *
 * `SharedPreferences` rather than the module's DataStore: this is written inside the database-open
 * path, which has no coroutine and cannot suspend - the same constraint the phone's notice records.
 */
object WearDatabaseResetNotice {

    /** Payload of a pending notice, cleared by the [consumePending] call that returns it. */
    data class PendingReset(val reason: String, val recoveredNotes: Int)

    /**
     * S3985: whether a first-open failure means the stored schema cannot be used - a migration Room
     * could not find or apply, an integrity mismatch, a corrupt file - so deleting the file is the only
     * way to a working database. A full disk, a lock or an unopenable file anywhere in the cause chain
     * vetoes the reset: those pass, and the owner's rows behind them are intact. Same contract as the
     * phone's `DatabaseResetNotice.isResettableOpenFailure`, which this module cannot import.
     */
    fun isResettableOpenFailure(error: Throwable): Boolean {
        val chain = generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
        if (chain.any(::isTransientOpenFailure)) {
            return false
        }
        return chain.any { it is SQLiteException || isRoomSchemaFailure(it) }
    }

    private fun isTransientOpenFailure(error: Throwable): Boolean =
        error is SQLiteFullException ||
            error is SQLiteDatabaseLockedException ||
            error is SQLiteCantOpenDatabaseException

    private fun isRoomSchemaFailure(error: Throwable): Boolean {
        val message = (error as? IllegalStateException)?.message.orEmpty()
        return ROOM_SCHEMA_MARKERS.any { message.contains(it, ignoreCase = true) }
    }

    /**
     * MUST NOT throw: it runs inside the recovery path, so a failure here would leave the provider
     * with no database at all - the exact outcome this ticket exists to prevent. Losing the notice
     * costs an explanation; losing the database costs the app.
     */
    @Suppress("TooGenericExceptionCaught")
    fun recordReset(context: Context, error: Throwable, recoveredNotes: Int) {
        try {
            prefs(context).edit()
                .putBoolean(KEY_PENDING, true)
                .putString(KEY_REASON, "${error.javaClass.simpleName}: ${error.message ?: UNKNOWN_REASON}")
                .putInt(KEY_RECOVERED_NOTES, recoveredNotes)
                .apply()
        } catch (e: RuntimeException) {
            Timber.w(e, "WearDatabaseResetNotice: failed to persist the notice")
        }
    }

    /** Consume-once: strategic 3.3 records the notice as shown a single time, then never again. */
    fun consumePending(context: Context): PendingReset? {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_PENDING, false)) {
            return null
        }
        val reason = prefs.getString(KEY_REASON, null) ?: UNKNOWN_REASON
        val recoveredNotes = prefs.getInt(KEY_RECOVERED_NOTES, 0)
        prefs.edit().clear().apply()
        return PendingReset(reason, recoveredNotes)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
