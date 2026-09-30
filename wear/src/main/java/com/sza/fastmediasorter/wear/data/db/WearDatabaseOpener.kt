package com.sza.fastmediasorter.wear.data.db

import android.content.Context
import androidx.room.RoomDatabase
import timber.log.Timber

/**
 * S3985: the one place a watch database may be deleted. Every provider used to catch any open
 * failure and delete the file, so a full disk or a lock became permanent loss of the owner's health
 * history. A failure [WearDatabaseResetNotice.isResettableOpenFailure] does not classify as a schema
 * failure is rethrown with the file left in place for the next launch.
 */
object WearDatabaseOpener {

    /**
     * Opens the database eagerly so a migration failure surfaces here, not at the first query.
     * [afterReset] runs on the recreated, already opened database - the place to rebuild derived rows.
     */
    @Suppress("TooGenericExceptionCaught")
    fun <T : RoomDatabase> openOrReset(
        context: Context,
        name: String,
        build: () -> T,
        afterReset: (T, RuntimeException) -> Unit = { _, _ -> }
    ): T = try {
        build().also { it.openHelper.writableDatabase }
    } catch (e: RuntimeException) {
        if (!WearDatabaseResetNotice.isResettableOpenFailure(e)) {
            Timber.e(e, "Wear database %s failed to open with a non-schema error - keeping the file", name)
            throw e
        }
        Timber.e(e, "Wear database %s failed to open with a schema error - recreating it", name)
        context.deleteDatabase(name)
        build().also {
            it.openHelper.writableDatabase
            afterReset(it, e)
        }
    }
}
