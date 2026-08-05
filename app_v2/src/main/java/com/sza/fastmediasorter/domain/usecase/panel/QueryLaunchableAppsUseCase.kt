package com.sza.fastmediasorter.domain.usecase.panel

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import com.sza.fastmediasorter.domain.repository.InstalledAppsRepository
import com.sza.fastmediasorter.domain.usecase.apps.RefreshInstalledAppsUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A launchable installed app: package + display label + icon. */
data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

/**
 * Lists installed launcher apps (excluding this app), sorted by label. Runs off the main thread.
 *
 * S1401: reads the cache rather than the package manager, so the app-launch panel's picker gets the
 * same speed-up as the launcher's own list (strategic ADR-1). The return type is unchanged on purpose -
 * both existing callers keep working without knowing where the list came from.
 */
class QueryLaunchableAppsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: InstalledAppsRepository,
    private val refreshInstalledApps: RefreshInstalledAppsUseCase
) {
    suspend operator fun invoke(): List<LaunchableApp> = withContext(Dispatchers.IO) {
        // Startup seeding may not have run yet - on a first launch, or right after the cache was
        // cleared. A caller that asks for the list must never get an empty one for that reason.
        if (repository.cachedCount() == 0) {
            refreshInstalledApps.refreshAll()
        }
        repository.observeApps().first()
            .sortedBy { it.label.lowercase() }
            .map { app -> LaunchableApp(app.packageName, app.label, iconOf(app)) }
    }

    /**
     * The cache directory is reclaimable, so a named icon file can be gone by the time it is read.
     * The platform's own placeholder keeps the contract - every entry has an icon - rather than
     * dropping an installed app from the list over a missing image.
     */
    private fun iconOf(app: InstalledApp): Drawable {
        val bitmap = app.iconFile?.let { BitmapFactory.decodeFile(it.path) }
            ?: return context.packageManager.defaultActivityIcon
        return BitmapDrawable(context.resources, bitmap)
    }
}
