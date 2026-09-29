package com.sza.fastmediasorter.data.local.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration58To59Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate58To59_collapsesDuplicatesAndKeepsCounters() {
        helper.createDatabase(TEST_DB, 58).use { old ->
            old.execSQL(insertJournal(TARGET_A, OLD_TIME))
            old.execSQL(insertJournal(TARGET_A, NEWEST_TIME))
            old.execSQL(insertJournal(TARGET_A, MIDDLE_TIME))
            old.execSQL(insertJournal(TARGET_B, OLD_TIME))
            old.execSQL(
                "INSERT INTO launcher_launch_stats (target, launchCount, lastLaunchedAt) " +
                    "VALUES ('$TARGET_A', $STATS_COUNT, $NEWEST_TIME)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 59, true, MIGRATION_58_59)

        db.query("SELECT COUNT(*) FROM launcher_journal").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("one row per program must remain", 2, cursor.getInt(0))
        }
        db.query("SELECT launchedAt FROM launcher_journal WHERE target = ?", arrayOf<Any>(TARGET_A))
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("the newest launch must survive", NEWEST_TIME, cursor.getLong(0))
            }
        db.query("SELECT COUNT(*) FROM launcher_journal WHERE target = ?", arrayOf<Any>(TARGET_B))
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("a program launched once must survive", 1, cursor.getInt(0))
            }
        db.query("SELECT launchCount FROM launcher_launch_stats WHERE target = ?", arrayOf<Any>(TARGET_A))
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("the launch counter must be untouched", STATS_COUNT, cursor.getInt(0))
            }

        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL(insertJournal(TARGET_A, NEWEST_TIME + 1))
        }
    }

    private fun insertJournal(target: String, launchedAt: Long): String =
        "INSERT INTO launcher_journal (target, launchedAt) VALUES ('$target', $launchedAt)"

    private companion object {
        const val TEST_DB = "migration-test-58-to-59"
        const val TARGET_A = "app:com.example.a"
        const val TARGET_B = "app:com.example.b"
        const val OLD_TIME = 1_000L
        const val MIDDLE_TIME = 2_000L
        const val NEWEST_TIME = 3_000L
        const val STATS_COUNT = 3
    }
}
