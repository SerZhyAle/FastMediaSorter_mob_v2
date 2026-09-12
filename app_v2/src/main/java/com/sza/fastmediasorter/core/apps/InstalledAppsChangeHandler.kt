package com.sza.fastmediasorter.core.apps

import android.content.Intent
import com.sza.fastmediasorter.domain.usecase.apps.RefreshInstalledAppsUseCase
import com.sza.fastmediasorter.domain.usecase.launcher.SyncInstalledAppShortcutUseCase
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2745: the one body that answers a package change, whichever receiver caught it.
 *
 * Two receivers exist and neither can be dropped: the manifest one still works on `legacy` (API 23),
 * and [InstalledAppsChangeWatcher] is the only one the platform talks to from API 26 up. A second copy
 * of this logic would let the two flavors drift apart silently, which is exactly how the install half
 * stayed dead after S2739 fixed the removal half.
 */
@Singleton
class InstalledAppsChangeHandler @Inject constructor(
    private val refreshInstalledApps: RefreshInstalledAppsUseCase,
    private val syncInstalledAppShortcut: SyncInstalledAppShortcutUseCase,
) {

    /**
     * An app update arrives as REMOVED+ADDED with `EXTRA_REPLACING` set on both halves. Ignoring the
     * removal half keeps an update from costing two sweeps, and from briefly deleting the row of an
     * app that never actually left.
     */
    suspend fun handle(action: String?, packageName: String, isReplacing: Boolean) {
        val replacingRemoval = action == Intent.ACTION_PACKAGE_REMOVED && isReplacing
        if (replacingRemoval) return
        refreshInstalledApps.refreshPackage(packageName)
        packageChange(action, isReplacing)?.let { change ->
            syncInstalledAppShortcut(packageName, change)
        }
    }

    companion object {
        fun packageChange(
            action: String?,
            isReplacing: Boolean,
        ): SyncInstalledAppShortcutUseCase.Change? {
            if (isReplacing) return null
            return when (action) {
                Intent.ACTION_PACKAGE_ADDED -> SyncInstalledAppShortcutUseCase.Change.INSTALLED
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_FULLY_REMOVED -> SyncInstalledAppShortcutUseCase.Change.REMOVED
                else -> null
            }
        }
    }
}
