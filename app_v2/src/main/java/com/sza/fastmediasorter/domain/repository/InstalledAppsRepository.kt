package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import kotlinx.coroutines.flow.Flow

/** S1401: how often and how recently one encoded launcher command was run. */
data class LaunchStats(
    val launchCount: Int,
    val lastLaunchedAt: Long
)

/**
 * S1401: the cached list of launchable installed apps.
 *
 * Lives in the shared layer rather than the launcher's, because the app-launch panel's picker reads
 * the same list in flavors that have no launcher mode at all (strategic ADR-1).
 *
 * Everything is a stream: a surface subscribes once and follows installs, removals and locale changes
 * without asking again.
 */
interface InstalledAppsRepository {

    fun observeApps(): Flow<List<InstalledApp>>

    /** Replaces the whole cache, dropping rows for apps that are no longer installed. */
    suspend fun replaceAll(apps: List<InstalledApp>)

    suspend fun upsert(app: InstalledApp)

    suspend fun remove(packageName: String)

    suspend fun cachedCount(): Int

    /** Lowest format version currently stored, or null when the cache is empty. */
    suspend fun cachedFormatVersion(): Int?

    /** Launch counters keyed by the encoded command, feeding the "most used" order. */
    fun observeLaunchStats(): Flow<Map<String, LaunchStats>>
}
