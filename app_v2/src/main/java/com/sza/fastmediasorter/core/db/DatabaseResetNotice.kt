package com.sza.fastmediasorter.core.db

import android.app.Activity
import android.content.Context
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.util.showBoundToHost
import timber.log.Timber
import java.io.File

/**
 * Records when the local Room database had to be destructively reset (open/migration failure) and
 * surfaces an explanatory dialog on the next Activity start instead of a silent wipe.
 *
 * S0731 decision: keep the destructive reset so the app stays usable, but (a) back up the previous
 * database files aside first, and (b) tell the user the failure reason and where the backup is -
 * rather than the prior silent Toast. The reset itself happens in DatabaseModule's open-recovery
 * path (no Activity exists yet), so the notice is persisted and shown by the first Activity.
 */
object DatabaseResetNotice {
    private const val PREFS = "database_reset_notice"
    private const val KEY_PENDING = "pending"
    private const val KEY_REASON = "reason"
    private const val KEY_BACKUP = "backup_path"
    private const val MAX_CAUSE_DEPTH = 8

    // Substrings of Room's own open-time errors: a missing migration path, a migration that left the
    // schema wrong, and an identity-hash mismatch.
    private val ROOM_SCHEMA_MARKERS = listOf("migration", "data integrity")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Whether an exception from the first open means the stored schema cannot be used - a migration
     * Room could not find or apply, an integrity mismatch, a corrupt file - so a reset is the only way
     * to a working database. Anything in the cause chain that is a full disk, a lock or a file that
     * could not be opened vetoes the reset: those pass, and the data behind them is intact.
     */
    fun isResettableOpenFailure(error: Throwable): Boolean {
        val chain = generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
        if (chain.any(::isTransientOpenFailure)) return false
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
     * Copies the existing DB files aside, then persists a pending notice. MUST NOT throw - it runs
     * inside the DB-open recovery path where a failure would leave the app without a database.
     * Call before [Context.deleteDatabase], and only when this returns true: false means a database
     * exists and no copy of it could be made, so deleting it would lose the user's data.
     */
    fun recordReset(context: Context, dbName: String, error: Throwable): Boolean {
        val backupPath = try {
            backupDatabase(context, dbName)
        } catch (e: Exception) {
            Timber.w(e, "DatabaseResetNotice: backup failed")
            null
        }
        if (backupPath == null && context.getDatabasePath(dbName).exists()) {
            Timber.e("DatabaseResetNotice: no backup of an existing database, reset refused")
            return false
        }
        try {
            prefs(context).edit()
                .putBoolean(KEY_PENDING, true)
                .putString(KEY_REASON, "${error.javaClass.simpleName}: ${error.message ?: "unknown"}")
                .putString(KEY_BACKUP, backupPath)
                .apply()
        } catch (e: Exception) {
            Timber.w(e, "DatabaseResetNotice: failed to persist notice")
        }
        return true
    }

    /** Copies <dbName>(+ -wal/-shm) into an app-scoped backup dir; returns the dir path or null. */
    private fun backupDatabase(context: Context, dbName: String): String? {
        val live = context.getDatabasePath(dbName)
        val parent = live.parentFile ?: return null
        if (!live.exists()) return null
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(baseDir, "db-backups/${System.currentTimeMillis()}")
        if (!dir.exists() && !dir.mkdirs()) return null
        var copiedAny = false
        for (suffix in listOf("", "-wal", "-shm")) {
            val src = File(parent, dbName + suffix)
            if (src.exists()) {
                src.copyTo(File(dir, src.name), overwrite = true)
                copiedAny = true
            }
        }
        return if (copiedAny) dir.absolutePath else null
    }

    /** Payload of a pending reset notice, consumed (and cleared) by [consumePending]. */
    data class PendingReset(val reason: String, val backupPath: String?)

    /**
     * IO-safe: read the pending reset notice and clear it (consume-once). Returns null when nothing
     * is pending. Split from the dialog step (S1153) so the SharedPreferences read runs off the main
     * thread; render the returned payload on Main via [showNotice].
     */
    fun consumePending(context: Context): PendingReset? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PENDING, false)) return null
        val reason = p.getString(KEY_REASON, null) ?: "unknown"
        val backup = p.getString(KEY_BACKUP, null)
        p.edit().clear().apply()
        return PendingReset(reason, backup)
    }

    /** Main-thread: render the reset notice consumed by [consumePending]. */
    fun showNotice(activity: Activity, notice: PendingReset) {
        if (activity.isFinishing) return
        val message = buildString {
            append(activity.getString(R.string.database_reset_dialog_message, notice.reason))
            if (!notice.backupPath.isNullOrBlank()) {
                append("\n\n")
                append(activity.getString(R.string.database_reset_backup_note, notice.backupPath))
            }
        }
        try {
            val dialog = MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.database_reset_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .create()
            DialogKeyboardDelegate.applyTo(dialog) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
            }
            dialog.showBoundToHost(activity)
        } catch (e: Exception) {
            Timber.w(e, "DatabaseResetNotice: failed to show dialog")
        }
    }
}
