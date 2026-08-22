package com.sza.fastmediasorter.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S1649: instrumented migration test for the orphan clock (schema 50 -> 51).
 *
 * The hop adds one nullable column by hand, so any divergence from what Room generates for the new
 * `Long?` field - its affinity, its default - surfaces as a crash on the first launch after upgrade
 * rather than at build time. `runMigrationsAndValidate` compares the migrated schema against the
 * exported 51.json and is what catches that.
 *
 * The pre-existing credential row carries the other half of the promise, and it is the half that
 * matters for this ticket specifically: an existing credential must survive the migration with its
 * fields intact and with the new column NULL. NULL is not a formality here - the policy treats a
 * missing orphan moment as "never eligible", so this assertion is what proves the update cannot make
 * anyone's stored password deletable on the spot.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration50To51Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate50To51_addsNullOrphanClockAndKeepsExistingCredential() {
        helper.createDatabase(TEST_DB, 50).use { old ->
            old.execSQL(
                "INSERT INTO network_credentials " +
                    "(credentialId, type, server, port, username, password, domain, " +
                    "manual_share_names, accountId, createdDate) " +
                    "VALUES ('kept-id', 'SMB', '192.168.1.10', 445, 'user', 'enc', '', '', '', 42)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 51, true, MIGRATION_50_51)

        db.query(
            "SELECT credentialId, server, createdDate, orphaned_since FROM network_credentials"
        ).use { cursor ->
            assertTrue("the pre-existing credential must survive the migration", cursor.moveToFirst())
            assertEquals("kept-id", cursor.getString(0))
            assertEquals("192.168.1.10", cursor.getString(1))
            assertEquals(42L, cursor.getLong(2))
            assertTrue("the orphan clock must arrive unset", cursor.isNull(3))
            assertEquals("exactly one row was inserted", 1, cursor.count)
        }
    }

    private companion object {
        const val TEST_DB = "migration-test-50-to-51"
    }
}
