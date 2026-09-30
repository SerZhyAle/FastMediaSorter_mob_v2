package com.sza.fastmediasorter.wear.data.db

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteFullException
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2356: the notice is the only thing that tells a watch owner why every delivery badge went back
 * to "not sent", so losing it turns a recovery into what looks like data loss.
 *
 * The last test is the load-bearing one: [WearDatabaseResetNotice.recordReset] runs inside the
 * database-open recovery, where throwing would leave the provider with no database at all - and
 * "never throws" is a claim only a test that makes it throw can support.
 */
class WearDatabaseResetNoticeTest {

    /**
     * Backed by a map, delegating everything it does not name to a relaxed mock so a future
     * `SharedPreferences` member cannot break this file.
     */
    private class FakePreferences(
        private val delegate: SharedPreferences = mockk(relaxed = true)
    ) : SharedPreferences by delegate {

        val values = mutableMapOf<String, Any?>()
        var editorThrows = false

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            values[key] as? Boolean ?: defValue

        override fun getString(key: String, defValue: String?): String? =
            values[key] as? String ?: defValue

        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

        override fun edit(): SharedPreferences.Editor = FakeEditor(values, editorThrows)
    }

    private class FakeEditor(
        private val target: MutableMap<String, Any?>,
        private val throwOnWrite: Boolean,
        private val delegate: SharedPreferences.Editor = mockk(relaxed = true)
    ) : SharedPreferences.Editor by delegate {

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = put(key, value)

        override fun putString(key: String, value: String?): SharedPreferences.Editor = put(key, value)

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = put(key, value)

        override fun clear(): SharedPreferences.Editor {
            target.clear()
            return this
        }

        override fun apply() = Unit

        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            if (throwOnWrite) {
                error("SharedPreferences is unavailable")
            }
            target[key] = value
            return this
        }
    }

    private fun contextOver(preferences: SharedPreferences): Context {
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns preferences
        return context
    }

    @Test
    fun `nothing is pending before a reset is recorded`() {
        val context = contextOver(FakePreferences())

        assertNull(WearDatabaseResetNotice.consumePending(context))
    }

    @Test
    fun `a recorded reset comes back with its reason and count`() {
        val context = contextOver(FakePreferences())

        WearDatabaseResetNotice.recordReset(context, IllegalStateException("schema mismatch"), 4)
        val pending = WearDatabaseResetNotice.consumePending(context)

        assertNotNull(pending)
        assertEquals("IllegalStateException: schema mismatch", pending!!.reason)
        assertEquals(4, pending.recoveredNotes)
    }

    @Test
    fun `a failure without a message still yields a readable reason`() {
        val context = contextOver(FakePreferences())

        WearDatabaseResetNotice.recordReset(context, IllegalStateException(), 0)
        val pending = WearDatabaseResetNotice.consumePending(context)

        assertNotNull(pending)
        assertTrue(pending!!.reason.startsWith("IllegalStateException:"))
    }

    @Test
    fun `the notice is consumed once`() {
        val context = contextOver(FakePreferences())

        WearDatabaseResetNotice.recordReset(context, IllegalStateException("boom"), 1)

        assertNotNull(WearDatabaseResetNotice.consumePending(context))
        assertNull(WearDatabaseResetNotice.consumePending(context))
    }

    @Test
    fun `recording returns normally when the store refuses the write`() {
        val preferences = FakePreferences().apply { editorThrows = true }
        val context = contextOver(preferences)

        WearDatabaseResetNotice.recordReset(context, IllegalStateException("boom"), 2)

        // The recovery continues without its explanation rather than losing the database.
        assertNull(WearDatabaseResetNotice.consumePending(context))
    }

    @Test
    fun `a missing migration path allows a reset`() {
        val error = IllegalStateException("A migration from 2 to 3 was required but not found.")
        assertTrue(WearDatabaseResetNotice.isResettableOpenFailure(error))
    }

    @Test
    fun `an integrity hash mismatch allows a reset`() {
        val error = IllegalStateException("Room cannot verify the data integrity. Looks like you've changed schema")
        assertTrue(WearDatabaseResetNotice.isResettableOpenFailure(error))
    }

    @Test
    fun `a corrupt file allows a reset`() {
        assertTrue(WearDatabaseResetNotice.isResettableOpenFailure(SQLiteDatabaseCorruptException("corrupt")))
    }

    @Test
    fun `a full disk never resets`() {
        assertFalse(WearDatabaseResetNotice.isResettableOpenFailure(SQLiteFullException("disk is full")))
    }

    @Test
    fun `a locked database never resets`() {
        assertFalse(WearDatabaseResetNotice.isResettableOpenFailure(SQLiteDatabaseLockedException("locked")))
    }

    @Test
    fun `an unopenable file never resets`() {
        assertFalse(WearDatabaseResetNotice.isResettableOpenFailure(SQLiteCantOpenDatabaseException("cannot open")))
    }

    @Test
    fun `a transient cause vetoes an outer schema-like error`() {
        val error = IllegalStateException("Migration failed", SQLiteFullException("disk is full"))
        assertFalse(WearDatabaseResetNotice.isResettableOpenFailure(error))
    }

    @Test
    fun `an unrelated exception never resets`() {
        assertFalse(WearDatabaseResetNotice.isResettableOpenFailure(IllegalArgumentException("bad argument")))
        assertFalse(WearDatabaseResetNotice.isResettableOpenFailure(IllegalStateException("closed pool")))
    }
}
