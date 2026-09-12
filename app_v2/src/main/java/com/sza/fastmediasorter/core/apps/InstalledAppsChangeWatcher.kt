package com.sza.fastmediasorter.core.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2745: keeps the installed-app cache in step with the system from API 26 up.
 *
 * The Android 8.0 background-execution limits apply to MANIFEST receivers only, and of the package
 * broadcasts only `PACKAGE_FULLY_REMOVED` is exempt from them - so an install or an update never
 * reaches [InstalledAppsChangeReceiver] on anything but the `legacy` flavor. A runtime registration
 * carries no such restriction, which is why this class exists.
 *
 * Registered for the life of the process rather than for the life of the all-apps screen: the same
 * cache feeds the quick-launch panel and the desktop, and a screen-scoped receiver would fix one of
 * the three. `LauncherApps.Callback` was rejected for the reason recorded on the manifest receiver -
 * it only reports while this app holds the home role.
 */
@Singleton
class InstalledAppsChangeWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val handler: InstalledAppsChangeHandler,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    private var receiver: BroadcastReceiver? = null

    /** Idempotent: a second call keeps the registration made by the first. */
    fun start() {
        if (receiver != null) return
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val packageName = intent.data?.schemeSpecificPart ?: return
                val action = intent.action
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                runInBackground(packageName) { handler.handle(action, packageName, isReplacing) }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme(PACKAGE_SCHEME)
        }
        ContextCompat.registerReceiver(context, listener, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = listener
    }

    /**
     * The symmetric edge of [start]. The process holds the registration for its whole life, so this
     * exists for the teardown paths that do have an end - instrumentation and any future owner that
     * stops following package changes - rather than for a lifecycle callback that fires today.
     */
    fun stop() {
        val listener = receiver ?: return
        receiver = null
        runCatching { context.unregisterReceiver(listener) }
            .onFailure { Timber.w(it, "InstalledAppsChangeWatcher: receiver was not registered") }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runInBackground(packageName: String, work: suspend () -> Unit) {
        applicationScope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed cache refresh must not take the process with it: this runs while the user
                // installs something unrelated, and the stale row is corrected by the next event or by
                // the startup reconcile.
                Timber.e(e, "Installed-app cache refresh failed (%s)", packageName)
            }
        }
    }

    private companion object {
        const val PACKAGE_SCHEME = "package"
    }
}
