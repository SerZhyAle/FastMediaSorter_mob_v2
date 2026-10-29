package com.sza.fastmediasorter.data.local.db

import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S4248: proves the SQL of [MIGRATION_62_63] on the JVM.
 *
 * [AppDatabaseMigration62To63Test] validates the resulting schema, which only Room's instrumented helper
 * can do, and nothing here runs the instrumented set. This test executes the same statements against
 * real SQLite: the database is opened at the current schema, `scheduled_operations` is replaced by its
 * version-62 shape by hand - the only table this hop touches - and the migration is run over it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration62To63SqlTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val db get() = dbRule.db.openHelper.writableDatabase

    @Test
    fun `an existing schedule survives and a watch message without a source can be stored`() {
        val resourceId = runBlocking {
            dbRule.db.resourceDao().insert(ResourceEntity(name = "Camera", path = "/dcim", type = ResourceType.LOCAL))
        }
        db.execSQL("DROP TABLE `scheduled_operations`")
        db.execSQL(CREATE_V62_TABLE)
        db.execSQL(insertCopy(resourceId))

        MIGRATION_62_63.migrate(db)

        db.query("SELECT source_resource_id, next_run_at, watch_title FROM scheduled_operations WHERE id = 11")
            .use { cursor ->
                assertTrue("the version-62 schedule must keep its id", cursor.moveToFirst())
                assertEquals(resourceId, cursor.getLong(0))
                assertEquals(NEXT_RUN_AT, cursor.getLong(1))
                assertNull(cursor.getString(2))
            }

        db.execSQL(INSERT_WATCH_MESSAGE)
        db.query("SELECT watch_title FROM scheduled_operations WHERE source_resource_id IS NULL").use { cursor ->
            assertTrue("a schedule with no source must be storable after the hop", cursor.moveToFirst())
            assertEquals("Water", cursor.getString(0))
        }
    }

    @Test
    fun `deleting the resource still removes its schedule but not a watch message`() {
        val resourceId = runBlocking {
            dbRule.db.resourceDao().insert(ResourceEntity(name = "Camera", path = "/dcim", type = ResourceType.LOCAL))
        }
        db.execSQL("DROP TABLE `scheduled_operations`")
        db.execSQL(CREATE_V62_TABLE)
        db.execSQL(insertCopy(resourceId))
        MIGRATION_62_63.migrate(db)
        db.execSQL(INSERT_WATCH_MESSAGE)
        db.execSQL("PRAGMA foreign_keys = ON")

        db.execSQL("DELETE FROM resources WHERE id = $resourceId")

        db.query("SELECT id FROM scheduled_operations WHERE id = 11").use { cursor ->
            assertFalse("the rebuilt table must keep ON DELETE CASCADE", cursor.moveToFirst())
        }
        db.query("SELECT id FROM scheduled_operations WHERE source_resource_id IS NULL").use { cursor ->
            assertTrue("a watch message depends on no resource", cursor.moveToFirst())
        }
    }

    private fun insertCopy(resourceId: Long) =
        "INSERT INTO scheduled_operations (id, is_enabled, source_resource_id, operation_type, " +
            "target_resource_id, file_type_mask, time_filter, start_time_hour, start_time_minute, " +
            "interval_hours, interval_minutes, overwrite, silent_mode, next_run_at) VALUES " +
            "(11, 1, $resourceId, 'DELETE', NULL, 1, 'ALL', 3, 0, 24, 0, 0, 0, $NEXT_RUN_AT)"

    private companion object {
        const val NEXT_RUN_AT = 1_800_000_000_000L

        const val INSERT_WATCH_MESSAGE =
            "INSERT INTO scheduled_operations (is_enabled, source_resource_id, operation_type, " +
                "target_resource_id, file_type_mask, time_filter, start_time_hour, start_time_minute, " +
                "interval_hours, interval_minutes, overwrite, silent_mode, watch_title) VALUES " +
                "(1, NULL, 'WATCH_MESSAGE', NULL, 1, 'ALL', 9, 0, 24, 0, 0, 0, 'Water')"

        const val CREATE_V62_TABLE =
            "CREATE TABLE IF NOT EXISTS `scheduled_operations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`is_enabled` INTEGER NOT NULL, `source_resource_id` INTEGER NOT NULL, " +
                "`operation_type` TEXT NOT NULL, `target_resource_id` INTEGER, `file_type_mask` INTEGER NOT NULL, " +
                "`time_filter` TEXT NOT NULL, `start_time_hour` INTEGER NOT NULL, " +
                "`start_time_minute` INTEGER NOT NULL, `interval_hours` INTEGER NOT NULL, " +
                "`interval_minutes` INTEGER NOT NULL, `overwrite` INTEGER NOT NULL, `silent_mode` INTEGER NOT NULL, " +
                "`last_run_at` INTEGER, `next_run_at` INTEGER, `last_run_status` TEXT, `worker_id` TEXT, " +
                "`file_name_mask` TEXT, `min_age_hours` INTEGER, `max_age_hours` INTEGER, " +
                "`min_size_bytes` INTEGER, `max_size_bytes` INTEGER, `last_success_at` INTEGER, " +
                "FOREIGN KEY(`source_resource_id`) REFERENCES `resources`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`target_resource_id`) REFERENCES `resources`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
    }
}
