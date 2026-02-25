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
        CachedFileListEntity::class
    ],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun resourceDao(): ResourceDao
    abstract fun networkCredentialsDao(): NetworkCredentialsDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun playbackPositionDao(): PlaybackPositionDao
    abstract fun thumbnailCacheDao(): ThumbnailCacheDao
    abstract fun cachedFileListDao(): CachedFileListDao
    
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
    }
}
