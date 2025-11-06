package com.sza.fastmediasorter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ConnectionConfig::class], version = 5, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionConfigDao(): ConnectionConfigDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Migration from version 2 to 3: Add support for local storage
         * - Added 'type' column with default 'SMB'
         * - Added 'localUri' column for local folder URIs
         * - Added 'localDisplayName' column for local folder display names
         */
        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE connection_configs ADD COLUMN type TEXT NOT NULL DEFAULT 'SMB'")
                    database.execSQL("ALTER TABLE connection_configs ADD COLUMN localUri TEXT")
                    database.execSQL("ALTER TABLE connection_configs ADD COLUMN localDisplayName TEXT")
                }
            }

        /**
         * Migration from version 3 to 4: Add slideshow position tracking
         * - Added 'lastSlideshowIndex' column to remember last viewed file position
         */
        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "ALTER TABLE connection_configs ADD COLUMN lastSlideshowIndex INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        /**
         * Migration from version 4 to 5: Add write permission tracking
         * - Added 'writePermission' column to track if folder allows write operations
         */
        private val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "ALTER TABLE connection_configs ADD COLUMN writePermission INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        /**
         * Returns all available migrations in order
         */
        private fun getAllMigrations(): Array<Migration> =
            arrayOf(
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                // Add future migrations here:
                // MIGRATION_5_6,
                // etc.
            )

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                val instance =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "fastmediasorter_database",
                        ).addMigrations(*getAllMigrations())
                        .addCallback(DatabaseMigrationHelper.databaseCallback)
                        // Removed fallbackToDestructiveMigration() to preserve user data
                        // If migration is missing, app will crash with clear error message
                        // forcing developers to add proper migration
                        .build()
                this.instance = instance
                instance
            }
    }
}
