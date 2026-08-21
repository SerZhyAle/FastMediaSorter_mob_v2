package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sza.fastmediasorter.data.util.StreamChannelIdentity
import timber.log.Timber

/**
 * Migration from schema version 51 to 52.
 * S1832: gives every catalog row its derived identity and creates `stream_user_state`, the table that
 * holds the user's own pin, pin order and last play outcome under that identity.
 *
 * Why the hop exists: `stream_sources.id` is a fresh UUID minted on every catalog import, so a channel
 * dropped from the published bank and re-added later comes back as a different row and loses the pin,
 * the position and the history filed under the old id. Identity is derived from the address, which the
 * publisher keeps stable, so it survives the round trip.
 *
 * The migration COPIES user data rather than moving it: `stream_sources.pinned`/`sortIndex` and the
 * `stream_play_outcome` table are left exactly as they were. A rollback to version 51 therefore loses
 * nothing, and the readers can be switched over one at a time in a later phase.
 *
 * The two CREATE statements are written out by hand to match what Room generates character for
 * character - `runMigrationsAndValidate` compares the migrated schema against the exported 52.json and
 * fails on any divergence, which is why they are not trusted to match by construction.
 */
private const val SCHEMA_VERSION_FROM = 51
private const val SCHEMA_VERSION_TO = 52

private const val ADD_IDENTITY_COLUMN =
    "ALTER TABLE `stream_sources` ADD COLUMN `identityKey` TEXT NOT NULL DEFAULT ''"

private const val CREATE_IDENTITY_INDEX =
    "CREATE INDEX IF NOT EXISTS `index_stream_sources_identityKey` ON `stream_sources` (`identityKey`)"

private const val CREATE_USER_STATE_TABLE =
    "CREATE TABLE IF NOT EXISTS `stream_user_state` (`identityKey` TEXT NOT NULL, " +
        "`pinned` INTEGER NOT NULL, `sortIndex` INTEGER NOT NULL, `playOutcome` TEXT, " +
        "`outcomeAt` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`identityKey`))"

private const val SELECT_ROWS = "SELECT `id`, `url` FROM `stream_sources`"

private const val UPDATE_IDENTITY =
    "UPDATE `stream_sources` SET `identityKey` = ? WHERE `id` = ?"

private const val SELECT_PINS =
    "SELECT `identityKey`, `sortIndex` FROM `stream_sources` WHERE `pinned` = 1 AND `identityKey` <> ''"

private const val SELECT_OUTCOMES =
    "SELECT s.`identityKey`, o.`outcome`, o.`recordedAt` FROM `stream_play_outcome` o " +
        "INNER JOIN `stream_sources` s ON s.`id` = o.`streamId` WHERE s.`identityKey` <> ''"

// Bind positions of INSERT_USER_STATE, named because a positional index silently desyncs from the
// column list the moment a column is inserted anywhere but at the end.
private const val BIND_IDENTITY = 1
private const val BIND_PINNED = 2
private const val BIND_SORT_INDEX = 3
private const val BIND_OUTCOME = 4
private const val BIND_OUTCOME_AT = 5
private const val BIND_UPDATED_AT = 6

private const val INSERT_USER_STATE =
    "INSERT OR REPLACE INTO `stream_user_state` " +
        "(`identityKey`, `pinned`, `sortIndex`, `playOutcome`, `outcomeAt`, `updatedAt`) " +
        "VALUES (?, ?, ?, ?, ?, ?)"

val MIGRATION_51_52 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.d("S1832: migration 51 -> 52 entered - backfilling identity, seeding stream_user_state")
        db.execSQL(ADD_IDENTITY_COLUMN)
        db.execSQL(CREATE_IDENTITY_INDEX)
        db.execSQL(CREATE_USER_STATE_TABLE)
        backfillIdentity(db)
        seedUserState(db)
    }
}

/**
 * The derivation parses an address, so it runs in Kotlin rather than as one UPDATE .. SET = expr: SQLite
 * has no function that folds a scheme and a default port. Read first, write second - rewriting rows
 * while the same statement's cursor walks them is what makes this kind of backfill lose rows.
 */
private fun backfillIdentity(db: SupportSQLiteDatabase) {
    val identities = mutableListOf<Pair<String, String>>()
    db.query(SELECT_ROWS).use { cursor ->
        while (cursor.moveToNext()) {
            identities += cursor.getString(0) to StreamChannelIdentity.of(cursor.getString(1))
        }
    }
    db.compileStatement(UPDATE_IDENTITY).use { statement ->
        for ((id, identity) in identities) {
            statement.clearBindings()
            statement.bindString(1, identity)
            statement.bindString(2, id)
            statement.executeUpdateDelete()
        }
    }
}

/**
 * Seeds the new table from what the device already holds. Both sources are small - a handful of pins and
 * one outcome row per channel the user actually played - so they are merged in memory, where the
 * collision rule is legible: two catalog rows sharing one identity are one channel entered into the bank
 * twice, so the pin with the higher position wins and the newest outcome wins.
 */
private fun seedUserState(db: SupportSQLiteDatabase) {
    val states = linkedMapOf<String, SeedState>()
    db.query(SELECT_PINS).use { cursor ->
        while (cursor.moveToNext()) {
            val key = cursor.getString(0)
            val sortIndex = cursor.getInt(1)
            val existing = states[key]
            if (existing == null) {
                states[key] = SeedState(pinned = true, sortIndex = sortIndex)
            } else if (sortIndex < existing.sortIndex) {
                existing.sortIndex = sortIndex
            }
        }
    }
    db.query(SELECT_OUTCOMES).use { cursor ->
        while (cursor.moveToNext()) {
            val key = cursor.getString(0)
            val state = states.getOrPut(key) { SeedState(pinned = false, sortIndex = 0) }
            val recordedAt = cursor.getLong(2)
            val previousAt = state.outcomeAt
            if (previousAt == null || recordedAt > previousAt) {
                state.outcome = cursor.getString(1)
                state.outcomeAt = recordedAt
            }
        }
    }
    writeUserState(db, states)
}

private fun writeUserState(db: SupportSQLiteDatabase, states: Map<String, SeedState>) {
    val now = System.currentTimeMillis()
    db.compileStatement(INSERT_USER_STATE).use { statement ->
        for ((key, state) in states) {
            statement.clearBindings()
            statement.bindString(BIND_IDENTITY, key)
            statement.bindLong(BIND_PINNED, if (state.pinned) 1L else 0L)
            statement.bindLong(BIND_SORT_INDEX, state.sortIndex.toLong())
            state.outcome?.let { statement.bindString(BIND_OUTCOME, it) }
            state.outcomeAt?.let { statement.bindLong(BIND_OUTCOME_AT, it) }
            statement.bindLong(BIND_UPDATED_AT, now)
            statement.executeInsert()
        }
    }
}

/** Mutable while the two source tables are folded together; never leaves this file. */
private class SeedState(
    val pinned: Boolean,
    var sortIndex: Int,
    var outcome: String? = null,
    var outcomeAt: Long? = null
)
