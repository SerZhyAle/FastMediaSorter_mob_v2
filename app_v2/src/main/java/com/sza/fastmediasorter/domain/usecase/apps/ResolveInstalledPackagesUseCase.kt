package com.sza.fastmediasorter.domain.usecase.apps

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * S1560: which of [candidates] this device actually has, asked of the package manager directly.
 *
 * Deliberately not backed by `InstalledAppsRepository`: that cache is filled by `DeferredStartupWorker`,
 * which by definition has not run yet on the first launch - the one moment the launcher desktop is seeded.
 * A conditional cell keyed off the cache would therefore be dropped for every app on the device.
 *
 * `getLaunchIntentForPackage` needs no flags and no `<queries>` addition: the manifest already declares the
 * MAIN/LAUNCHER intent, which grants visibility of every launchable app on API 30+.
 */
class ResolveInstalledPackagesUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    suspend operator fun invoke(candidates: Set<String>): Set<String> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        candidates.filterTo(mutableSetOf()) { packageManager.getLaunchIntentForPackage(it) != null }
    }
}
