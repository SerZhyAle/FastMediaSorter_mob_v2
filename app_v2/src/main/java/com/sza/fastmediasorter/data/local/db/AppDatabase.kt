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
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add allFiles column to resources table with default false
                db.execSQL("ALTER TABLE resources ADD COLUMN allFiles INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add showHiddenFiles column to resources table with default false
                db.execSQL("ALTER TABLE resources ADD COLUMN showHiddenFiles INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add showSubfoldersAsItems column to resources table (nullable, null = use global setting)
                db.execSQL("ALTER TABLE resources ADD COLUMN showSubfoldersAsItems INTEGER DEFAULT NULL")
            }
        }
        
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add subfolderCount column to resources table with default 0
                db.execSQL("ALTER TABLE resources ADD COLUMN subfolderCount INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Convert showSubfoldersAsItems from nullable to non-nullable (null → false)
                // Global settings are now only defaults for NEW resources, each resource has its own binary setting
                
                // Step 1: Update NULL values to 0
                db.execSQL("UPDATE resources SET showSubfoldersAsItems = 0 WHERE showSubfoldersAsItems IS NULL")
                
                // Step 2: Create new table with NOT NULL constraint
                db.execSQL("""
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
                """)
                
                // Step 3: Copy data from old table to new table
                db.execSQL("""
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
                        COALESCE(showSubfoldersAsItems, 0) as showSubfoldersAsItems,
                        subfolderCount
                    FROM resources
                """)
                
                // Step 4: Drop old table
                db.execSQL("DROP TABLE resources")
                
                // Step 5: Rename new table to original name
                db.execSQL("ALTER TABLE resources_new RENAME TO resources")
                
                // Step 6: Recreate indices
                db.execSQL("CREATE INDEX idx_resources_display_order_name ON resources (displayOrder, name)")
                db.execSQL("CREATE INDEX idx_resources_type_display_order_name ON resources (type, displayOrder, name)")
                db.execSQL("CREATE INDEX idx_resources_is_destination_order ON resources (isDestination, destinationOrder)")
                db.execSQL("CREATE INDEX idx_resources_media_types ON resources (supportedMediaTypesFlags)")
            }
        }
        
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Fix broken databases from incomplete MIGRATION_5_6
                // Check if showSubfoldersAsItems is nullable and fix it
                
                // Query current schema to check if column is nullable
                val cursor = db.query("PRAGMA table_info(resources)")
                var isShowSubfoldersNullable = false
                val nameColumnIndex = cursor.getColumnIndexOrThrow("name")
                val notNullColumnIndex = cursor.getColumnIndexOrThrow("notnull")
                
                while (cursor.moveToNext()) {
                    val columnName = cursor.getString(nameColumnIndex)
                    val notNull = cursor.getInt(notNullColumnIndex)
                    if (columnName == "showSubfoldersAsItems" && notNull == 0) {
                        isShowSubfoldersNullable = true
                        break
                    }
                }
                cursor.close()
                
                // If column is nullable, we need to recreate the table
                if (isShowSubfoldersNullable) {
                    // Step 1: Update NULL values to 0
                    db.execSQL("UPDATE resources SET showSubfoldersAsItems = 0 WHERE showSubfoldersAsItems IS NULL")
                    
                    // Step 2: Create new table with NOT NULL constraint
                    db.execSQL("""
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
                    """)
                    
                    // Step 3: Copy data from old table to new table
                    db.execSQL("""
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
                            COALESCE(showSubfoldersAsItems, 0) as showSubfoldersAsItems,
                            subfolderCount
                        FROM resources
                    """)
                    
                    // Step 4: Drop old table
                    db.execSQL("DROP TABLE resources")
                    
                    // Step 5: Rename new table to original name
                    db.execSQL("ALTER TABLE resources_new RENAME TO resources")
                    
                    // Step 6: Recreate indices
                    db.execSQL("CREATE INDEX idx_resources_display_order_name ON resources (displayOrder, name)")
                    db.execSQL("CREATE INDEX idx_resources_type_display_order_name ON resources (type, displayOrder, name)")
                    db.execSQL("CREATE INDEX idx_resources_is_destination_order ON resources (isDestination, destinationOrder)")
                    db.execSQL("CREATE INDEX idx_resources_media_types ON resources (supportedMediaTypesFlags)")
                }
                // If column is already NOT NULL, do nothing - database is already correct
            }
        }
        
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add rememberFileList column to resources table
                db.execSQL("ALTER TABLE resources ADD COLUMN rememberFileList INTEGER NOT NULL DEFAULT 0")
                
                // 2. Create cached_file_lists table
                db.execSQL("""
                    CREATE TABLE cached_file_lists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        resourceId INTEGER NOT NULL,
                        media_file_json TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(resourceId) REFERENCES resources(id) ON DELETE CASCADE
                    )
                """)
                
                // 3. Create index on resourceId
                db.execSQL("CREATE INDEX idx_cached_files_resource_id ON cached_file_lists (resourceId)")
            }
        }

        /**
         * v8 → v9: Replace N-rows-per-resource plain JSON with 1-row-per-resource GZIP BLOB.
         * Existing cache is intentionally dropped — it will be repopulated on next browse.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS cached_file_lists")
                db.execSQL("""
                    CREATE TABLE cached_file_lists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        resourceId INTEGER NOT NULL,
                        compressed_data BLOB NOT NULL,
                        file_count INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(resourceId) REFERENCES resources(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX idx_cached_files_resource_id ON cached_file_lists (resourceId)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add resource profile field for quick-setup presets
                db.execSQL("ALTER TABLE resources ADD COLUMN profile TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        /**
         * v10 → v11: Add missing indexes for query optimization (B4).
         * - network_credentials: credentialId (unique), type+server+port (composite)
         * - resources: credentialsId, cloudProvider
         * - favorites: resourceId
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Some legacy databases may contain duplicate credentialId values.
                // Keep one row per credentialId before creating UNIQUE index.
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

                // network_credentials indexes
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_credentials_credential_id ON network_credentials (credentialId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_credentials_type_server_port ON network_credentials (type, server, port)")

                // resources indexes
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_resources_credentials_id ON resources (credentialsId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_resources_cloud_provider ON resources (cloudProvider)")

                // favorites indexes
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_favorites_resource_id ON favorites (resourceId)")
            }
        }

        /**
         * v11 → v12: Add file_metadata_cache table for incremental scan and
         * per-file metadata caching (A5: Scan Optimization).
         *
         * Stores per-file metadata (mtime, size, thumbnail path, duration, resolution,
         * EXIF) indexed by (resourceId, filePath) for O(1) cache lookups during scan.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
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
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_fmc_resource_path ON file_metadata_cache (resourceId, filePath)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_fmc_credentials_id ON file_metadata_cache (credentialsId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_fmc_cached_at ON file_metadata_cache (cachedAt)")
            }
        }
        /**
         * v12 → v13: Add scan state fields to cached_file_lists (A5-T1).
         * - last_scan_timestamp: when the most recent scan completed (epoch ms, nullable).
         * - last_modified_folder: folder mtime at scan time for change detection (nullable).
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_file_lists ADD COLUMN last_scan_timestamp INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE cached_file_lists ADD COLUMN last_modified_folder INTEGER DEFAULT NULL")
            }
        }

        /**
         * v13 → v14: Add pending_revocations table for deferred OAuth token revocation (B5-T3).
         * Stores tokens that could not be revoked synchronously during sign-out;
         * WorkManager retries until the provider acknowledges (HTTP 200/400 invalid = done).
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_revocations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        provider TEXT NOT NULL,
                        token TEXT NOT NULL,
                        revokeUrl TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        attemptCount INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pending_rev_provider ON pending_revocations (provider)")
            }
        }

        /**
         * v14 → v15: Add accountId column to network_credentials table for Cloud Multi-Account support (A1).
         * Used to map credentials to specific cloud accounts (e.g. email).
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE network_credentials ADD COLUMN accountId TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_credentials_account_id ON network_credentials (accountId)")
            }
        }

        /**
         * v15 → v16: Add videoRotation and exifDateTime columns to file_metadata_cache (A5).
         * These nullable columns were added to FileMetadataCacheEntity but the original
         * MIGRATION_11_12 that created the table did not include them.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE file_metadata_cache ADD COLUMN videoRotation INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE file_metadata_cache ADD COLUMN exifDateTime INTEGER DEFAULT NULL")
            }
        }

        /**
         * v16 → v17: Fix pending_revocations table schema mismatch.
         * MIGRATION_13_14 created wrong index name (idx_pending_rev_provider) and
         * set DEFAULT 0 for attemptCount, but Room expects no DEFAULT (defaultValue='undefined').
         * Table is drop-and-recreated; pending revocation queue is ephemeral.
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS pending_revocations")
                db.execSQL("""
                    CREATE TABLE pending_revocations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        provider TEXT NOT NULL,
                        token TEXT NOT NULL,
                        revokeUrl TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        attemptCount INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_revocations_provider ON pending_revocations (provider)")
            }
        }

        /**
         * v17 → v18: Add audio metadata columns (artist, album, title) to file_metadata_cache.
         * These were extracted by CachedMediaMetadataExtractor but never persisted,
         * causing cache-hit reads to lose audio metadata.
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE file_metadata_cache ADD COLUMN artist TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE file_metadata_cache ADD COLUMN album TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE file_metadata_cache ADD COLUMN title TEXT DEFAULT NULL")
            }
        }
    }
}
