package com.sza.fastmediasorter.core.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.usecase.apps.RefreshInstalledAppsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S1401: keeps the installed-app cache in step with the system.
 *
 * A manifest receiver rather than `LauncherApps.Callback`, which only reports while this app holds the
 * home role - it would go silent in every flavor without launcher mode, and the moment the user hands
 * the role back, which is exactly when a stale list is hardest to notice (strategic research 03). The
 * package broadcasts are exempt from the implicit-broadcast restrictions, so this still fires with the
 * app not running.
 */
@AndroidEntryPoint
class InstalledAppsChangeReceiver : BroadcastReceiver() {

    @Inject
    lateinit var refreshInstalledApps: RefreshInstalledAppsUseCase

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private companion object {
        /** Names the locale branch in a failure log, where there is no package name to name it by. */
        const val LOCALE_REASON = "locale change"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCALE_CHANGED ->
                runInBackground(LOCALE_REASON) { refreshInstalledApps.refreshLabelsOnly() }
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED -> refreshChangedPackage(intent)
            else -> Unit
        }
    }

    /**
     * An app update arrives as REMOVED+ADDED with `EXTRA_REPLACING` set on both halves. Ignoring the
     * removal half keeps an update from costing two sweeps, and from briefly deleting the row of an
     * app that never actually left.
     */
    private fun refreshChangedPackage(intent: Intent) {
        val replacingRemoval = intent.action == Intent.ACTION_PACKAGE_REMOVED &&
            intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        val packageName = intent.data?.schemeSpecificPart
        if (packageName != null && !replacingRemoval) {
            runInBackground(packageName) { refreshInstalledApps.refreshPackage(packageName) }
        }
    }

    /**
     * The refresh outlives `onReceive`, so the broadcast is held open for it. `goAsync` is what buys
     * that time: without it the process is a candidate for death the moment this method returns, and
     * the cache would follow the system only when the user happened to keep the app alive.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun runInBackground(reason: String, work: suspend () -> Unit) {
        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A cache refresh that failed must not take the process with it: this runs for a
                // system broadcast, so an uncaught throw here would surface as the app crashing while
                // the user installs something unrelated. The stale row is corrected by the next event.
                Timber.e(e, "Installed-app cache refresh failed (%s)", reason)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
