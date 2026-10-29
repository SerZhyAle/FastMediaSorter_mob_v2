package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val SCHEMA_VERSION_FROM = 62
private const val SCHEMA_VERSION_TO = 63

private const val CREATE_SCHEDULED_OPERATIONS_V63 =
    "CREATE TABLE IF NOT EXISTS `scheduled_operations_new` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `is_enabled` INTEGER NOT NULL, " +
        "`source_resource_id` INTEGER, `operation_type` TEXT NOT NULL, `target_resource_id` INTEGER, " +
        "`file_type_mask` INTEGER NOT NULL, `time_filter` TEXT NOT NULL, `start_time_hour` INTEGER NOT NULL, " +
        "`start_time_minute` INTEGER NOT NULL, `interval_hours` INTEGER NOT NULL, " +
        "`interval_minutes` INTEGER NOT NULL, `overwrite` INTEGER NOT NULL, `silent_mode` INTEGER NOT NULL, " +
        "`last_run_at` INTEGER, `next_run_at` INTEGER, `last_run_status` TEXT, `worker_id` TEXT, " +
        "`file_name_mask` TEXT, `min_age_hours` INTEGER, `max_age_hours` INTEGER, `min_size_bytes` INTEGER, " +
        "`max_size_bytes` INTEGER, `last_success_at` INTEGER, `watch_title` TEXT, `watch_text` TEXT, " +
        "`watch_image_uri` TEXT, " +
        "FOREIGN KEY(`source_resource_id`) REFERENCES `resources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
        "FOREIGN KEY(`target_resource_id`) REFERENCES `resources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

private const val V62_COLUMNS =
    "`id`, `is_enabled`, `source_resource_id`, `operation_type`, `target_resource_id`, `file_type_mask`, " +
        "`time_filter`, `start_time_hour`, `start_time_minute`, `interval_hours`, `interval_minutes`, " +
        "`overwrite`, `silent_mode`, `last_run_at`, `next_run_at`, `last_run_status`, `worker_id`, " +
        "`file_name_mask`, `min_age_hours`, `max_age_hours`, `min_size_bytes`, `max_size_bytes`, `last_success_at`"

private const val COPY_SCHEDULED_OPERATIONS =
    "INSERT INTO `scheduled_operations_new` ($V62_COLUMNS) SELECT $V62_COLUMNS FROM `scheduled_operations`"

private val RECREATE_INDICES = listOf(
    "CREATE INDEX IF NOT EXISTS `idx_sched_ops_source` ON `scheduled_operations` (`source_resource_id`)",
    "CREATE INDEX IF NOT EXISTS `idx_sched_ops_target` ON `scheduled_operations` (`target_resource_id`)",
    "CREATE INDEX IF NOT EXISTS `idx_sched_ops_enabled` ON `scheduled_operations` (`is_enabled`)",
)

/**
 * S4248: a schedule that sends a message to the paired watch reads no resource, so `source_resource_id`
 * becomes nullable, and the message's title, text and picture URI get their own columns.
 *
 * The table is rebuilt because SQLite cannot drop NOT NULL through ALTER TABLE. Every existing row is
 * copied with its id, so WorkManager's `sched_op_<id>` names keep pointing at the same schedule, and the
 * three indices are re-created after the rename because DROP TABLE takes them with the old table.
 */
val MIGRATION_62_63 = object : Migration(SCHEMA_VERSION_FROM, SCHEMA_VERSION_TO) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_SCHEDULED_OPERATIONS_V63)
        db.execSQL(COPY_SCHEDULED_OPERATIONS)
        db.execSQL("DROP TABLE `scheduled_operations`")
        db.execSQL("ALTER TABLE `scheduled_operations_new` RENAME TO `scheduled_operations`")
        RECREATE_INDICES.forEach(db::execSQL)
    }
}
