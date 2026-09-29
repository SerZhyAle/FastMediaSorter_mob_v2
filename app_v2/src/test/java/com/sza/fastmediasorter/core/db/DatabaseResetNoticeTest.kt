package com.sza.fastmediasorter.core.db

import android.content.Context
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins which first-open failures may wipe the database: only a schema that cannot be used, never a
 * transient condition that leaves the data intact, and never an existing file with no backup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class DatabaseResetNoticeTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `a missing migration path allows a reset`() {
        val error = IllegalStateException("A migration from 57 to 58 was required but not found.")
        assertTrue(DatabaseResetNotice.isResettableOpenFailure(error))
    }

    @Test
    fun `an integrity hash mismatch allows a reset`() {
        val error = IllegalStateException("Room cannot verify the data integrity. Looks like you've changed schema")
        assertTrue(DatabaseResetNotice.isResettableOpenFailure(error))
    }

    @Test
    fun `a corrupt file allows a reset`() {
        assertTrue(DatabaseResetNotice.isResettableOpenFailure(SQLiteDatabaseCorruptException("corrupt")))
    }

    @Test
    fun `a wrapped schema failure allows a reset`() {
        val error = RuntimeException("open failed", SQLiteException("no such table: resources"))
        assertTrue(DatabaseResetNotice.isResettableOpenFailure(error))
    }

    @Test
    fun `a full disk never resets`() {
        assertFalse(DatabaseResetNotice.isResettableOpenFailure(SQLiteFullException("database or disk is full")))
    }

    @Test
    fun `a locked database never resets`() {
        assertFalse(DatabaseResetNotice.isResettableOpenFailure(SQLiteDatabaseLockedException("locked")))
    }

    @Test
    fun `an unopenable file never resets`() {
        assertFalse(DatabaseResetNotice.isResettableOpenFailure(SQLiteCantOpenDatabaseException("cannot open")))
    }

    @Test
    fun `a transient cause vetoes an outer schema-like error`() {
        val error = IllegalStateException("Migration failed", SQLiteFullException("disk is full"))
        assertFalse(DatabaseResetNotice.isResettableOpenFailure(error))
    }

    @Test
    fun `an unrelated exception never resets`() {
        assertFalse(DatabaseResetNotice.isResettableOpenFailure(IllegalArgumentException("bad argument")))
        assertFalse(DatabaseResetNotice.isResettableOpenFailure(IllegalStateException("closed pool")))
    }

    @Test
    fun `no existing database needs no backup to reset`() {
        val name = "s3820_absent.db"
        context.deleteDatabase(name)
        assertTrue(DatabaseResetNotice.recordReset(context, name, IllegalStateException("migration")))
    }

    @Test
    fun `an existing database is backed up before a reset`() {
        val name = "s3820_present.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.writeText("payload")
        assertTrue(DatabaseResetNotice.recordReset(context, name, IllegalStateException("migration")))
        assertTrue(file.exists())
        context.deleteDatabase(name)
    }
}
