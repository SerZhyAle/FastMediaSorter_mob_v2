package com.sza.fastmediasorter.data.repository

import android.content.Context
import com.sza.fastmediasorter.data.local.db.InstalledAppDao
import com.sza.fastmediasorter.data.local.db.InstalledAppEntity
import com.sza.fastmediasorter.data.local.db.LauncherLaunchStatsDao
import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import com.sza.fastmediasorter.domain.repository.InstalledAppsRepository
import com.sza.fastmediasorter.domain.repository.LaunchStats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Subdirectory of the app cache directory holding one image per cached app icon. Declared here
 * because this class is the first reader of it; the icon store writing into it takes the same
 * constant rather than a second copy of the name.
 */
internal const val INSTALLED_APP_ICON_DIR = "installed_app_icons"

/** Written by the refresh path; a row from an older build is rebuilt rather than migrated. */
internal const val INSTALLED_APP_CACHE_FORMAT_VERSION = 1

class InstalledAppsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDao: InstalledAppDao,
    private val statsDao: LauncherLaunchStatsDao
) : InstalledAppsRepository {

    private val iconDirectory: File
        get() = File(context.cacheDir, INSTALLED_APP_ICON_DIR)

    override fun observeApps(): Flow<List<InstalledApp>> =
        appDao.observeAll()
            .map { rows -> rows.map(::toModel) }
            .flowOn(Dispatchers.IO)

    override suspend fun replaceAll(apps: List<InstalledApp>) = withContext(Dispatchers.IO) {
        appDao.upsertAll(apps.map(::toEntity))
        // Rows first, removals second: a reader that lands between the two sees a superset of the
        // truth rather than a list briefly missing apps that are still installed.
        appDao.deleteMissing(apps.map { it.packageName })
    }

    override suspend fun upsert(app: InstalledApp) = withContext(Dispatchers.IO) {
        appDao.upsert(toEntity(app))
    }

    override suspend fun remove(packageName: String) = withContext(Dispatchers.IO) {
        appDao.deleteByPackage(packageName)
    }

    override suspend fun cachedCount(): Int = withContext(Dispatchers.IO) { appDao.count() }

    override suspend fun cachedFormatVersion(): Int? =
        withContext(Dispatchers.IO) { appDao.lowestFormatVersion() }

    override fun observeLaunchStats(): Flow<Map<String, LaunchStats>> =
        statsDao.observeAll()
            .map { rows ->
                rows.associate { row -> row.target to LaunchStats(row.launchCount, row.lastLaunchedAt) }
            }
            .flowOn(Dispatchers.IO)

    private fun toModel(entity: InstalledAppEntity) = InstalledApp(
        packageName = entity.packageName,
        label = entity.label,
        firstInstallTime = entity.firstInstallTime,
        lastUpdateTime = entity.lastUpdateTime,
        category = entity.category,
        isSystemApp = entity.isSystemApp,
        // The cache directory is reclaimable by the system, so a named file that is gone is an
        // ordinary state: report no icon rather than a path that cannot be opened.
        iconFile = entity.iconFileName
            ?.let { File(iconDirectory, it) }
            ?.takeIf { it.isFile }
    )

    private fun toEntity(app: InstalledApp) = InstalledAppEntity(
        packageName = app.packageName,
        label = app.label,
        labelSortKey = app.label.lowercase(),
        firstInstallTime = app.firstInstallTime,
        lastUpdateTime = app.lastUpdateTime,
        category = app.category,
        isSystemApp = app.isSystemApp,
        iconFileName = app.iconFile?.name,
        cacheFormatVersion = INSTALLED_APP_CACHE_FORMAT_VERSION,
        refreshedAt = System.currentTimeMillis()
    )
}
