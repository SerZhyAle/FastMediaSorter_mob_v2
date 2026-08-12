package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * S1401: one launchable installed app, cached so a surface never waits for the package manager.
 *
 * [labelSortKey] is the lowercased label stored alongside the label itself: ordering by name is the
 * default order of the all-apps screen, and lowercasing a hundred labels on every keystroke is work
 * the write path can do once instead.
 *
 * [iconFileName] points into the icon cache directory rather than carrying bytes - a hundred icons
 * would otherwise put megabytes of binary into this database, and every unrelated query, backup and
 * migration would carry them (strategic research 02).
 *
 * [cacheFormatVersion] is what a later build compares against to decide that the whole cache must be
 * rebuilt. Cache rows are derived data, recoverable from the system, so a format change is a rebuild,
 * never a migration.
 */
@Entity(tableName = "installed_app_cache")
data class InstalledAppEntity(
    @PrimaryKey
    val packageName: String,
    val label: String,
    val labelSortKey: String,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val category: Int,
    val isSystemApp: Boolean,
    val iconFileName: String?,
    val cacheFormatVersion: Int,
    val refreshedAt: Long
)

@Dao
interface InstalledAppDao {

    /**
     * Unordered on purpose: the all-apps screen picks its order at runtime from a persisted setting,
     * so a fixed ORDER BY here would only be overridden downstream.
     */
    @Query("SELECT * FROM installed_app_cache")
    fun observeAll(): Flow<List<InstalledAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<InstalledAppEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InstalledAppEntity)

    @Query("DELETE FROM installed_app_cache WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("DELETE FROM installed_app_cache WHERE packageName NOT IN (:packageNames)")
    suspend fun deleteMissing(packageNames: List<String>)

    @Query("SELECT COUNT(*) FROM installed_app_cache")
    suspend fun count(): Int

    @Query("SELECT MIN(cacheFormatVersion) FROM installed_app_cache")
    suspend fun lowestFormatVersion(): Int?
}
