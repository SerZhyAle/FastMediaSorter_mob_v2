package com.sza.fastmediasorter.domain.usecase.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.sza.fastmediasorter.data.launcher.InstalledAppIconStore
import com.sza.fastmediasorter.data.repository.INSTALLED_APP_CACHE_FORMAT_VERSION
import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import com.sza.fastmediasorter.domain.repository.InstalledAppsRepository
import com.sza.fastmediasorter.util.getPackageInfoCompat
import com.sza.fastmediasorter.util.queryIntentActivitiesCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S1401: fills the installed-app cache from the system.
 *
 * Three entry points instead of one, because the events that invalidate the cache differ in cost: a
 * single package install must not pay for a full sweep, and a locale change invalidates labels while
 * every icon file stays valid (strategic research 03). Nothing here touches the UI - the surfaces
 * read the repository's stream and see the result whenever it lands.
 */
class RefreshInstalledAppsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: InstalledAppsRepository,
    private val iconStore: InstalledAppIconStore
) {

    /**
     * Fills the cache when it is empty or was written by an older build, and does nothing otherwise.
     * This is the startup entry point: the screen must open without a wait on the first entry after a
     * reboot, which only holds if something filled the cache before the user asked for the list.
     *
     * An older format version rebuilds rather than migrates - every row is derived from the system and
     * cheaper to re-read than to convert.
     */
    suspend fun refreshIfStale() {
        val current = repository.cachedFormatVersion()
        val fresh = current == INSTALLED_APP_CACHE_FORMAT_VERSION && repository.cachedCount() > 0
        if (!fresh) {
            refreshAll()
        }
    }

    /** Rebuilds the whole cache and drops the icon files of apps that are no longer installed. */
    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val apps = launchableActivities(packageManager).mapNotNull { resolveInfo ->
            toInstalledApp(packageManager, resolveInfo)
        }
        if (apps.isEmpty()) {
            // Not "the device has no apps" - a device always has some - but a query that came back
            // empty. Feeding it forward would wipe the cache: SQLite reads `NOT IN ()` as always true,
            // so the replace deletes every row, and the retain sweep then deletes every icon file.
            Timber.i("Installed-app enumeration returned nothing, keeping the previous cache")
            return@withContext
        }
        repository.replaceAll(apps)
        iconStore.retainOnly(apps.mapTo(mutableSetOf()) { it.packageName })
    }

    /**
     * Brings one package up to date. An app that no longer resolves to a launchable activity - removed,
     * or hidden by its own update - loses its row and its icon together.
     */
    suspend fun refreshPackage(packageName: String) = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val resolveInfo = launchableActivities(packageManager)
            .firstOrNull { it.activityInfo?.packageName == packageName }
        if (resolveInfo == null) {
            repository.remove(packageName)
            iconStore.delete(packageName)
            return@withContext
        }
        toInstalledApp(packageManager, resolveInfo)?.let { repository.upsert(it) }
    }

    /**
     * Re-reads labels only. Icons are locale-independent, so rewriting a hundred image files after a
     * language change would be pure cost.
     */
    suspend fun refreshLabelsOnly() = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val resolved = launchableActivities(packageManager)
            .mapNotNull { info -> info.activityInfo?.packageName?.let { it to info } }
            .toMap()
        repository.observeApps().first().forEach { app ->
            val label = resolved[app.packageName]?.loadLabel(packageManager)?.toString()
            if (label != null && label != app.label) {
                repository.upsert(app.copy(label = label))
            }
        }
    }

    private fun launchableActivities(packageManager: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivitiesCompat(intent)
            .distinctBy { it.activityInfo?.packageName }
    }

    private suspend fun toInstalledApp(
        packageManager: PackageManager,
        resolveInfo: ResolveInfo
    ): InstalledApp? {
        val packageName = resolveInfo.activityInfo?.packageName
            ?.takeIf { it != context.packageName }
        val packageInfo = packageName?.let { packageInfoOrNull(packageManager, it) }
        return if (packageName == null || packageInfo == null) {
            null
        } else {
            val applicationInfo = packageInfo.applicationInfo
            InstalledApp(
                packageName = packageName,
                label = resolveInfo.loadLabel(packageManager).toString(),
                firstInstallTime = packageInfo.firstInstallTime,
                lastUpdateTime = packageInfo.lastUpdateTime,
                category = categoryOf(applicationInfo),
                isSystemApp = applicationInfo != null &&
                    applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                iconFile = iconStore.fileFor(
                    iconStore.write(packageName, resolveInfo.loadIcon(packageManager))
                )
            )
        }
    }

    private fun packageInfoOrNull(packageManager: PackageManager, packageName: String): PackageInfo? =
        try {
            packageManager.getPackageInfoCompat(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            // Uninstalled between the enumeration and this read. Skipping it is the correct result:
            // the sweep that follows must not carry a row for a package that is already gone.
            Timber.i(e, "Package %s vanished during refresh", packageName)
            null
        }

    /**
     * The publisher-declared category, which only exists from API 26. The `legacy` flavor still ships
     * to API 23, where every app reads as uncategorised and the category order simply groups them all
     * together rather than failing.
     */
    private fun categoryOf(applicationInfo: ApplicationInfo?): Int = when {
        applicationInfo == null -> CATEGORY_UNDEFINED
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> applicationInfo.category
        else -> CATEGORY_UNDEFINED
    }

    private companion object {
        /** `ApplicationInfo.CATEGORY_UNDEFINED`, spelled out so API 23 never resolves the constant. */
        const val CATEGORY_UNDEFINED = -1
    }
}
