package com.sza.fastmediasorter_v2.data.local.db

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
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun resourceDao(): ResourceDao
    abstract fun networkCredentialsDao(): NetworkCredentialsDao
    
    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE resources ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
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
    }
}
