package com.sza.fastmediasorter.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ResourceEntity::class,
        NetworkCredentialsEntity::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun resourceDao(): ResourceDao
    abstract fun networkCredentialsDao(): NetworkCredentialsDao
    
    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE resources ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS network_credentials (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        credentialId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        server TEXT NOT NULL,
                        port INTEGER NOT NULL,
                        username TEXT NOT NULL,
                        password TEXT NOT NULL,
                        domain TEXT NOT NULL,
                        shareName TEXT,
                        createdDate INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
        
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add cloud provider fields for CLOUD type resources
                db.execSQL("ALTER TABLE resources ADD COLUMN cloudProvider TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE resources ADD COLUMN cloudFolderId TEXT DEFAULT NULL")
            }
        }
        
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add availability indicator for resources
                db.execSQL("ALTER TABLE resources ADD COLUMN isAvailable INTEGER NOT NULL DEFAULT 1")
            }
        }
        
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add user-specific command panel preference (null = use global default)
                db.execSQL("ALTER TABLE resources ADD COLUMN showCommandPanel INTEGER DEFAULT NULL")
            }
        }
        
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add SSH private key field for SFTP authentication
                db.execSQL("ALTER TABLE network_credentials ADD COLUMN sshPrivateKey TEXT DEFAULT NULL")
            }
        }
        
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add last browse date for resources
                db.execSQL("ALTER TABLE resources ADD COLUMN lastBrowseDate INTEGER DEFAULT NULL")
            }
        }
        
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add last sync date for network resources
                db.execSQL("ALTER TABLE resources ADD COLUMN lastSyncDate INTEGER DEFAULT NULL")
            }
        }
        
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add indexes for performance optimization
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_resources_display_order_name ON resources(displayOrder, name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_resources_type_display_order_name ON resources(type, displayOrder, name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_resources_is_destination_order ON resources(isDestination, destinationOrder)")
            }
        }
    }
}
