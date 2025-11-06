package com.sza.fastmediasorter.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migration error handler and recovery utilities
 */
object DatabaseMigrationHelper {
    private const val TAG = "DatabaseMigration"

    /**
     * Callback for handling database creation and migration events
     */
    val databaseCallback =
        object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Log.i(TAG, "Database created with version ${db.version}")
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                Log.i(TAG, "Database opened with version ${db.version}")
            }
        }

    /**
     * Check if database exists and is accessible
     */
    fun isDatabaseAccessible(context: Context): Boolean =
        try {
            val db =
                Room
                    .databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "fastmediasorter_database",
                    ).build()

            // Try to access database
            db.openHelper.readableDatabase.version
            db.close()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Database is not accessible", e)
            false
        }

    /**
     * Get current database version
     */
    fun getCurrentDatabaseVersion(context: Context): Int =
        try {
            val db =
                Room
                    .databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "fastmediasorter_database",
                    ).build()

            val version = db.openHelper.readableDatabase.version
            db.close()
            version
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get database version", e)
            -1
        }

    /**
     * Emergency database reset (USE WITH CAUTION)
     * This will delete all user data!
     */
    fun emergencyReset(context: Context): Boolean =
        try {
            Log.w(TAG, "EMERGENCY DATABASE RESET - ALL DATA WILL BE LOST!")

            val dbFile = context.getDatabasePath("fastmediasorter_database")
            val walFile = context.getDatabasePath("fastmediasorter_database-wal")
            val shmFile = context.getDatabasePath("fastmediasorter_database-shm")

            var success = true
            if (dbFile.exists()) success = success && dbFile.delete()
            if (walFile.exists()) success = success && walFile.delete()
            if (shmFile.exists()) success = success && shmFile.delete()

            Log.i(TAG, if (success) "Database reset successful" else "Database reset failed")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset database", e)
            false
        }

    /**
     * Create database with error handling
     */
    fun createDatabaseSafely(context: Context): AppDatabase? =
        try {
            AppDatabase.getDatabase(context)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Migration failed - missing migration path", e)

            // Show user-friendly error message
            throw DatabaseMigrationException(
                "Database migration failed. Please update the app or contact support.",
                e,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create database", e)
            throw DatabaseMigrationException(
                "Failed to initialize database. Please try restarting the app.",
                e,
            )
        }
}

/**
 * Custom exception for database migration errors
 */
class DatabaseMigrationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
