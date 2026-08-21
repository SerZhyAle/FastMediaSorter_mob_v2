package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber

/**
 * Migration from schema version 52 to 53.
 * S1832: moves the last kind of user-authored data still filed under a catalog row id - the desktop
 * cell that launches a channel - onto the identity, and drops the table nothing reads any more.
 *
 * Why the rewrite: a `stream:` cell stored the row's UUID, and a catalog import mints a fresh UUID for
 * every row, so a channel dropped from the published bank and re-added later left the cell pointing at
 * an id that no longer existed. The reader falls back to the id permanently (a backup taken before this
 * change still carries that form), but a payload left as an id degrades again on the next prune.
 *
 * A target whose row is already gone is left exactly as it was. There is nothing to derive an identity
 * from, and blanking it would delete a cell the user placed - the failure strategic §7 rates as the one
 * costing exactly what this ticket promises to keep.
 *
 * `stream_play_outcome` goes because [MIGRATION_51_52] copied every outcome into `stream_user_state`
 * and Phase 03 removed the last reader. Rolling back to 52 therefore loses no history: the surviving
 * copy is the one the code reads.
 */
private const val SCHEMA_VERSION_FROM = 52
private const val SCHEMA_VERSION_TO = 53

// SUBSTR(target, 8) is the payload after the literal `stream:` prefix, which is 7 characters.
// Two independent conditions on purpose: the EXISTS decides whether a row is rewritable at all, and
// the sub-select in SET reads the value. Written as one statement rather than a Kotlin loop because,
// unlike the identity backfill, nothing here has to be parsed - SQLite can do the whole join itself.
private const val REWRITE_STREAM_TARGETS =
    "UPDATE `launcher_cells` SET `target` = 'stream:' || (" +
        "SELECT s.`identityKey` FROM `stream_sources` s WHERE s.`id` = SUBSTR(`target`, 8)" +
        ") WHERE `target` LIKE 'stream:%' AND EXISTS (" +
        "SELECT 1 FROM `stream_sources` s WHERE s.`id` = SUBSTR(`launcher_cells`.`target`, 8) " +
        "AND s.`identityKey` <> ''" +
        ")"

private const val DROP_PLAY_OUTCOME_TABLE = "DROP TABLE IF EXISTS `stream_play_outcome`"

val MIGRATION_52_53 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val rewritten = rewriteStreamCellTargets(db)
        // Logged even at zero, because a device log otherwise cannot tell "no stream cells to rewrite"
        // apart from "the rewrite never ran" - and those two call for opposite investigations.
        Timber.i("Launcher stream cells re-addressed to channel identity: %d", rewritten)
        db.execSQL(DROP_PLAY_OUTCOME_TABLE)
    }
}

private fun rewriteStreamCellTargets(db: SupportSQLiteDatabase): Int =
    db.compileStatement(REWRITE_STREAM_TARGETS).use { it.executeUpdateDelete() }
