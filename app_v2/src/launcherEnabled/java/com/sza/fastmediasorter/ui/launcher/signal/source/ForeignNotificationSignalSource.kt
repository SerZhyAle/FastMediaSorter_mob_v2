package com.sza.fastmediasorter.ui.launcher.signal.source

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.launcher.gadget.nowplaying.NotificationAccessState
import com.sza.fastmediasorter.ui.launcher.signal.ForeignNotificationCounts
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignal
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalIcon
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalKind
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalSource
import com.sza.fastmediasorter.util.getApplicationInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * S1465: one chip per application that currently has notifications pending, behind the same
 * [LauncherSignalSource] seam the app's own signals use (ADR-1).
 *
 * Carries no notification content by construction - it reads counts from [ForeignNotificationCounts], whose
 * API cannot hold a title or a text, and turns each into an icon, a name and a number.
 */
class ForeignNotificationSignalSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val counts: ForeignNotificationCounts,
) : LauncherSignalSource {

    /**
     * Empty whenever the system grant is missing, which is how §6.1's answer to a revoked access is
     * implemented: the registry already tolerates a silent source, so the strip simply returns to the app's
     * own signals with no prompt anywhere. The grant is re-read on every emission rather than cached -
     * `NotificationAccessState` is built for exactly that, because the user can revoke it while the launcher
     * is on screen.
     *
     * On IO because resolving an application's name goes through the package manager, and a status strip is
     * the last place that may touch disk on the main thread.
     */
    override fun observe(): Flow<List<LauncherSignal>> = counts.counts.map { byPackage ->
        Timber.d("S1465: foreign notification signals rebuilt, apps=${byPackage.size}")
        if (byPackage.isEmpty() || !NotificationAccessState.isEnabled(context)) {
            emptyList()
        } else {
            byPackage.map { (packageName, count) -> signalFor(packageName, count) }
        }
    }.flowOn(Dispatchers.IO)

    private fun signalFor(packageName: String, count: Int): LauncherSignal = LauncherSignal(
        id = SIGNAL_ID_PREFIX + packageName,
        kind = LauncherSignalKind.FOREIGN_NOTIFICATION,
        icon = LauncherSignalIcon.Application(packageName, fallbackRes = R.drawable.ic_apps),
        label = labelOf(packageName),
        detail = count.toString(),
    )

    /**
     * Falls back to the package name rather than throwing: an application can be uninstalled between the
     * count being taken and this list being built, and a strip that crashed on that race would be worse than
     * one showing a raw package name for a moment (strategic §7).
     */
    private fun labelOf(packageName: String): String = try {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfoCompat(packageName),
        ).toString()
    } catch (notInstalled: PackageManager.NameNotFoundException) {
        Timber.d(notInstalled, "Foreign notification signal: %s has no readable label", packageName)
        packageName
    }

    /**
     * @return the application's own launch intent, or null when it declares none - a service-only or
     * device-admin package can post a notification and still have no screen to open, and the strip is built
     * to leave such a chip unclickable rather than to start a no-op activity.
     */
    override fun open(signal: LauncherSignal): Intent? {
        if (!signal.id.startsWith(SIGNAL_ID_PREFIX)) {
            return null
        }
        val packageName = signal.id.removePrefix(SIGNAL_ID_PREFIX)
        return context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    private companion object {
        const val SIGNAL_ID_PREFIX = "foreign-notification:"
    }
}
