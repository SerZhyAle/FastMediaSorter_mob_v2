package com.sza.fastmediasorter.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ResourceEntity::class,
        NetworkCredentialsEntity::class,
        ResourceFtsEntity::class,
        FavoritesEntity::class,
        PlaybackPositionEntity::class,
        ThumbnailCacheEntity::class,
        CachedFileListEntity::class,
        FileMetadataCacheEntity::class,
        PendingRevocationEntity::class
    ],
    version = 18,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun resourceDao(): ResourceDao
    abstract fun networkCredentialsDao(): NetworkCredentialsDao
    abstract fun pendingRevocationDao(): PendingRevocationDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun playbackPositionDao(): PlaybackPositionDao
    abstract fun thumbnailCacheDao(): ThumbnailCacheDao
    abstract fun cachedFileListDao(): CachedFileListDao
    abstract fun fileMetadataCacheDao(): FileMetadataCacheDao

    companion object {
        /**
         * Direct migration for the short-lived "reset to v1" builds.
         * Those builds created a v1 database that already contained the modern schema,
         * so this migration must be schema-aware and idempotent.
         */
        val MIGRATION_1_18 = object : Migration(1, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateSchemaToV18(db)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE resources ADD COLUMN showHiddenFiles INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE resources ADD COLUMN showSubfoldersAsItems INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE resources ADD COLUMN subfolderCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE resources SET showSubfoldersAsItems = 0 WHERE showSubfoldersAsItems IS NULL")
                recreateResourcesTableWithNonNullShowSubfolders(db)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (isColumnNullable(db, "resources", "showSubfoldersAsItems")) {
                    db.execSQL("UPDATE resources SET showSubfoldersAsItems = 0 WHERE showSubfoldersAsItems IS NULL")
                    recreateResourcesTableWithNonNullShowSubfolders(db)
                }
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE resources ADD COLUMN rememberFileList INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE cached_file_lists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        resourceId INTEGER NOT NULL,
                        media_file_json TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(resourceId) REFERENCES resources(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX idx_cached_files_resource_id ON cached_file_lists (resourceId)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS cached_file_lists")
                createFinalCachedFileListsTable(db)
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE resources ADD COLUMN profile TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM network_credentials
                    WHERE id NOT IN (
                        SELECT MIN(id)
                        FROM network_credentials
                        GROUP BY credentialId
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_credentials_credential_id ON network_credentials (credentialId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_credentials_type_server_port ON network_credentials (type, server, port)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_resources_credentials_id ON resources (credentialsId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_resources_cloud_provider ON resources (cloudProvider)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_favorites_resource_id ON favorites (resourceId)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createBaseFileMetadataCacheTable(db)
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "cached_file_lists", "last_scan_timestamp")) {
                    db.execSQL("ALTER TABLE cached_file_lists ADD COLUMN last_scan_timestamp INTEGER DEFAULT NULL")
                }
                if (!hasColumn(db, "cached_file_lists", "last_modified_folder")) {
                    db.execSQL("ALTER TABLE cached_file_lists ADD COLUMN last_modified_folder INTEGER DEFAULT NULL")
                }
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_revocations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        provider TEXT NOT NULL,
                        token TEXT NOT NULL,
                        revokeUrl TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        attemptCount INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pending_rev_provider ON pending_revocations (provider)")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "network_credentials", "accountId")) {
                    db.execSQL("ALTER TABLE network_credentials ADD COLUMN accountId TEXT NOT NULL DEFAULT ''")
                }
                ensureIndex(db, "idx_credentials_account_id", "CREATE INDEX idx_credentials_account_id ON network_credentials (accountId)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "file_metadata_cache", "videoRotation")) {
                    db.execSQL("ALTER TABLE file_metadata_cache ADD COLUMN videoRotation INTEGER DEFAULT NULL")
                }
                if (!hasColumn(db, "file_metadata_cache", "exifDateTime")) {
                    db.execSQL("ALTER TABLE file_metadata_cache ADD COLUMN exifDateTime INTEGER DEFAULT NULL")
                }
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS pending_revocations")
                createFinalPendingRevocationsTable(db)
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "file_metadata_cache", "artist")) {
                    db.execSQL("ALTER TABLE file_metadata_cache ADD COLUMN artist TEXT DEFAULT NULL")
                }
                if (!hasColumn(db, "file_metadata_cache", "album")) {
                    db.execSQL("ALTER TABLE file_metadata_cache ADD COLUMN album TEXT DEFAULT NULL")
                }
                if (!hasColumn(db, "file_metadata_cache", "title")) {
                    db.execSQL("ALTER TABLE file_metadata_cache ADD COLUMN title TEXT DEFAULT NULL")
                }
            }
        }

        private fun migrateSchemaToV18(db: SupportSQLiteDatabase) {
            if (!hasColumn(db, "resources", "allFiles")) {
                db.execSQL("ALTER TABLE resources ADD COLUMN allFiles INTEGER NOT NULL DEFAULT 0")
            }
            if (!hasColumn(db, "resources", "showHiddenFiles")) {
                db.execSQL("ALTER TABLE resources ADD COLUMN showHiddenFiles INTEGER NOT NULL DEFAULT 0")
            }
            if (!hasColumn(db, "resources", "showSubfoldersAsItems")) {
                db.execSQL("ALTER TABLE resources ADD COLUMN showSubfoldersAsItems INTEGER NOT NULL DEFAULT 0")
            }
            if (!hasColumn(db, "resources", "subfolderCount")) {
                db.execSQL("ALTER TABLE resources ADD COLUMN subfolderCount INTEGER NOT NULL DEFAULT 0")
            }
            if (hasColumn(db, "resources", "showSubfoldersAsItems") && isColumnNullable(db, "resources", "showSubfoldersAsItems")) {
                db.execSQL("UPDATE resources SET showSubfoldersAsItems = 0 WHERE showSubfoldersAsItems IS NULL")
                recreateResourcesTableWithNonNullShowSubfolders(db)
            }
            if (!hasColumn(db, "resources", "rememberFileList")) {
                db.execSQL("ALTER TABLE resources ADD COLUMN rememberFileList INTEGER NOT NULL DEFAULT 0")
            }
            if (!hasColumn(db, "resources", "profile")) {
                db.execSQL("ALTER TABLE resources ADD COLUMN profile TEXT NOT NULL DEFAULT 'NONE'")
            }
            ensureIndex(db, "idx_resources_display_order_name", "CREATE INDEX idx_resources_display_order_name ON resources (displayOrder, name)")
            ensureIndex(db, "idx_resources_type_display_order_name", "CREATE INDEX idx_resources_type_display_order_name ON resources (type, displayOrder, name)")
            ensureIndex(db, "idx_resources_is_destination_order", "CREATE INDEX idx_resources_is_destination_order ON resources (isDestination, destinationOrder)")
            ensureIndex(db, "idx_resources_media_types", "CREATE INDEX idx_resources_media_types ON resources (supportedMediaTypesFlags)")
            ensureIndex(db, "idx_resources_credentials_id", "CREATE INDEX idx_resources_credentials_id ON resources (credentialsId)")
            ensureIndex(db, "idx_resources_cloud_provider", "CREATE INDEX idx_resources_cloud_provider ON resources (cloudProvider)")

            if (!hasTable(db, "cached_file_lists")) {
                createFinalCachedFileListsTable(db)
            } else {
                val needsRecreate = !hasColumn(db, "cached_file_lists", "compressed_data") || !hasColumn(db, "cached_file_lists", "file_count")
                if (needsRecreate) {
                    db.execSQL("DROP TABLE IF EXISTS cached_file_lists")
                    createFinalCachedFileListsTable(db)
                } else {
                    if (!hasColumn(db, "cached_file_lists", "last_scan_timestamp")) {
                        db.execSQL("ALTER TABLE cached_file_lists ADD COLUMN last_scan_timestamp INTEGER DEFAULT NULL")
                    }
                    if (!hasColumn(db, "cached_file_lists", "last_modified_folder")) {
                        db.execSQL("ALTER TABLE cached_file_lists ADD COLUMN last_modified_folder INTEGER DEFAULT NULL")
                    }
                    ensureIndex(db, "idx_cached_files_resource_id", "CREATE UNIQUE INDEX idx_cached_files_resource_id ON cached_file_lists (resourceId)")
                }
            }

            if (!hasTable(db, "file_metadata_cache")) {
                createFinalFileMetadataCacheTable(db)
            } else {
                if (!hasColumn(db, "file_metadata_cache", "videoRotation")) {
                    db.execSQL("ALTER TABLE file_metadata_cache ADD COLUMN videoRotation INTEGER DEFAULT NULL")
                }
                if (!hasColumn(db, "file_metadata_cache", "exifDateTime")) {
                    db.execSQL("ALTER TABLE file_metadata_cache ADD COLUMN exifDateTime INTEGER DEFAULT NULL")
                }
                if (!hasColumn(db, "file_metadata_cache", "artist")) {
                    db.execSQL("ALTER TABLE file_metadata_cache ADD COLUMN artist TEXT DEFAULT NULL")
                }
                if (!hasColumn(db, "file_metadata_cache", "album")) {
                    db.execSQL("ALTER TABLE file_metadata_cache ADD COLUMN album TEXT DEFAULT NULL")
                }
                if (!hasColumn(db, "file_metadata_cache", "title")) {
                    db.execSQL("ALTER TABLE file_metadata_cache ADD COLUMN title TEXT DEFAULT NULL")
                }
                ensureIndex(db, "idx_fmc_resource_path", "CREATE UNIQUE INDEX idx_fmc_resource_path ON file_metadata_cache (resourceId, filePath)")
                ensureIndex(db, "idx_fmc_credentials_id", "CREATE INDEX idx_fmc_credentials_id ON file_metadata_cache (credentialsId)")
                ensureIndex(db, "idx_fmc_cached_at", "CREATE INDEX idx_fmc_cached_at ON file_metadata_cache (cachedAt)")
            }

            if (!hasTable(db, "pending_revocations")) {
                createFinalPendingRevocationsTable(db)
            } else if (!hasIndex(db, "index_pending_revocations_provider")) {
                db.execSQL("DROP TABLE IF EXISTS pending_revocations")
                createFinalPendingRevocationsTable(db)
            }

            if (!hasColumn(db, "network_credentials", "accountId")) {
                db.execSQL("ALTER TABLE network_credentials ADD COLUMN accountId TEXT NOT NULL DEFAULT ''")
            }
            ensureIndex(db, "idx_credentials_credential_id", "CREATE UNIQUE INDEX idx_credentials_credential_id ON network_credentials (credentialId)")
            ensureIndex(db, "idx_credentials_type_server_port", "CREATE INDEX idx_credentials_type_server_port ON network_credentials (type, server, port)")
            ensureIndex(db, "idx_credentials_account_id", "CREATE INDEX idx_credentials_account_id ON network_credentials (accountId)")
            ensureIndex(db, "idx_favorites_resource_id", "CREATE INDEX idx_favorites_resource_id ON favorites (resourceId)")
        }

        private fun recreateResourcesTableWithNonNullShowSubfolders(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE resources_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    path TEXT NOT NULL,
                    type TEXT NOT NULL,
                    credentialsId TEXT,
                    cloudProvider TEXT,
                    cloudFolderId TEXT,
                    supportedMediaTypesFlags INTEGER NOT NULL,
                    sortMode TEXT NOT NULL,
                    displayMode TEXT NOT NULL,
                    lastViewedFile TEXT,
                    lastScrollPosition INTEGER NOT NULL,
                    fileCount INTEGER NOT NULL,
                    lastAccessedDate INTEGER NOT NULL,
                    slideshowInterval INTEGER NOT NULL,
                    isDestination INTEGER NOT NULL,
                    destinationOrder INTEGER NOT NULL,
                    destinationColor INTEGER NOT NULL,
                    isWritable INTEGER NOT NULL,
                    isReadOnly INTEGER NOT NULL,
                    isAvailable INTEGER NOT NULL,
                    showCommandPanel INTEGER,
                    createdDate INTEGER NOT NULL,
                    lastBrowseDate INTEGER,
                    lastSyncDate INTEGER,
                    scanSubdirectories INTEGER NOT NULL,
                    disableThumbnails INTEGER NOT NULL,
                    allFiles INTEGER NOT NULL,
                    showHiddenFiles INTEGER NOT NULL,
                    displayOrder INTEGER NOT NULL,
                    accessPin TEXT,
                    comment TEXT,
                    read_speed_mbps REAL,
                    write_speed_mbps REAL,
                    recommended_threads INTEGER,
                    last_speed_test_date INTEGER,
                    showSubfoldersAsItems INTEGER NOT NULL DEFAULT 0,
                    subfolderCount INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO resources_new
                SELECT
                    id, name, path, type, credentialsId, cloudProvider, cloudFolderId,
                    supportedMediaTypesFlags, sortMode, displayMode, lastViewedFile,
                    lastScrollPosition, fileCount, lastAccessedDate, slideshowInterval,
                    isDestination, destinationOrder, destinationColor, isWritable,
                    isReadOnly, isAvailable, showCommandPanel, createdDate, lastBrowseDate,
                    lastSyncDate, scanSubdirectories, disableThumbnails, allFiles,
                    showHiddenFiles, displayOrder, accessPin, comment, read_speed_mbps,
                    write_speed_mbps, recommended_threads, last_speed_test_date,
                    COALESCE(showSubfoldersAsItems, 0), subfolderCount
                FROM resources
                """.trimIndent()
            )
            db.execSQL("DROP TABLE resources")
            db.execSQL("ALTER TABLE resources_new RENAME TO resources")
            ensureIndex(db, "idx_resources_display_order_name", "CREATE INDEX idx_resources_display_order_name ON resources (displayOrder, name)")
            ensureIndex(db, "idx_resources_type_display_order_name", "CREATE INDEX idx_resources_type_display_order_name ON resources (type, displayOrder, name)")
            ensureIndex(db, "idx_resources_is_destination_order", "CREATE INDEX idx_resources_is_destination_order ON resources (isDestination, destinationOrder)")
            ensureIndex(db, "idx_resources_media_types", "CREATE INDEX idx_resources_media_types ON resources (supportedMediaTypesFlags)")
        }

        private fun createFinalCachedFileListsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS cached_file_lists (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    resourceId INTEGER NOT NULL,
                    compressed_data BLOB NOT NULL,
                    file_count INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    last_scan_timestamp INTEGER DEFAULT NULL,
                    last_modified_folder INTEGER DEFAULT NULL,
                    FOREIGN KEY(resourceId) REFERENCES resources(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            ensureIndex(db, "idx_cached_files_resource_id", "CREATE UNIQUE INDEX idx_cached_files_resource_id ON cached_file_lists (resourceId)")
        }

        private fun createBaseFileMetadataCacheTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS file_metadata_cache (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    resourceId INTEGER NOT NULL,
                    filePath TEXT NOT NULL,
                    provider TEXT NOT NULL,
                    credentialsId TEXT,
                    lastModified INTEGER NOT NULL,
                    fileSize INTEGER NOT NULL,
                    cachedAt INTEGER NOT NULL,
                    thumbnailPath TEXT,
                    durationMs INTEGER,
                    width INTEGER,
                    height INTEGER,
                    exifJson TEXT,
                    FOREIGN KEY(resourceId) REFERENCES resources(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            ensureIndex(db, "idx_fmc_resource_path", "CREATE UNIQUE INDEX idx_fmc_resource_path ON file_metadata_cache (resourceId, filePath)")
            ensureIndex(db, "idx_fmc_credentials_id", "CREATE INDEX idx_fmc_credentials_id ON file_metadata_cache (credentialsId)")
            ensureIndex(db, "idx_fmc_cached_at", "CREATE INDEX idx_fmc_cached_at ON file_metadata_cache (cachedAt)")
        }

        private fun createFinalFileMetadataCacheTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS file_metadata_cache (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    resourceId INTEGER NOT NULL,
                    filePath TEXT NOT NULL,
                    provider TEXT NOT NULL,
                    credentialsId TEXT,
                    lastModified INTEGER NOT NULL,
                    fileSize INTEGER NOT NULL,
                    cachedAt INTEGER NOT NULL,
                    thumbnailPath TEXT,
                    durationMs INTEGER,
                    width INTEGER,
                    height INTEGER,
                    videoRotation INTEGER,
                    exifDateTime INTEGER,
                    exifJson TEXT,
                    artist TEXT,
                    album TEXT,
                    title TEXT,
                    FOREIGN KEY(resourceId) REFERENCES resources(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            ensureIndex(db, "idx_fmc_resource_path", "CREATE UNIQUE INDEX idx_fmc_resource_path ON file_metadata_cache (resourceId, filePath)")
            ensureIndex(db, "idx_fmc_credentials_id", "CREATE INDEX idx_fmc_credentials_id ON file_metadata_cache (credentialsId)")
            ensureIndex(db, "idx_fmc_cached_at", "CREATE INDEX idx_fmc_cached_at ON file_metadata_cache (cachedAt)")
        }

        private fun createFinalPendingRevocationsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pending_revocations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    provider TEXT NOT NULL,
                    token TEXT NOT NULL,
                    revokeUrl TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    attemptCount INTEGER NOT NULL
                )
                """.trimIndent()
            )
            ensureIndex(db, "index_pending_revocations_provider", "CREATE INDEX index_pending_revocations_provider ON pending_revocations (provider)")
        }

        private fun hasTable(db: SupportSQLiteDatabase, tableName: String): Boolean {
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName)).use { cursor ->
                return cursor.moveToFirst()
            }
        }

        private fun hasIndex(db: SupportSQLiteDatabase, indexName: String): Boolean {
            db.query("SELECT name FROM sqlite_master WHERE type='index' AND name=?", arrayOf(indexName)).use { cursor ->
                return cursor.moveToFirst()
            }
        }

        private fun ensureIndex(db: SupportSQLiteDatabase, indexName: String, createSql: String) {
            if (!hasIndex(db, indexName)) {
                db.execSQL(createSql)
            }
        }

        private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            db.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == columnName) {
                        return true
                    }
                }
            }
            return false
        }

        private fun isColumnNullable(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            db.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                val notNullIndex = cursor.getColumnIndex("notnull")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == columnName) {
                        return cursor.getInt(notNullIndex) == 0
                    }
                }
            }
            return false
        }
    }
}
