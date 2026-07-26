package com.sza.fastmediasorter.data.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.Process
import com.sza.fastmediasorter.domain.model.launcher.AppShortcut
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0427: the single seam onto [LauncherApps] - the published quick actions of installed apps.
 *
 * Every call is guarded twice. `hasShortcutHostPermission()` is only true while this app holds the
 * home role, and the role can be revoked between that check and the call itself, so the service calls
 * are also wrapped: a revoked role is an expected state of the world here, not a crash.
 *
 * Blocking by design (binder IPC plus icon decode) - callers move it off the main thread.
 */
@Singleton
class AppShortcutDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val launcherApps: LauncherApps?
        get() = context.getSystemService(LauncherApps::class.java)

    /** True only while this app is the active launcher; without it the service refuses every query. */
    fun isHostPermitted(): Boolean = runCatching {
        launcherApps?.hasShortcutHostPermission() == true
    }.getOrElse { error ->
        Timber.i("Launcher shortcuts: host permission unavailable (%s)", error.javaClass.simpleName)
        false
    }

    /** Published manifest + dynamic shortcuts of [packageName], in the order the platform returned. */
    fun query(packageName: String): List<AppShortcut> {
        if (!isHostPermitted()) return emptyList()
        val service = launcherApps ?: return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
            )
        val infos = try {
            service.getShortcuts(query, Process.myUserHandle())
        } catch (e: SecurityException) {
            Timber.i(e, "Launcher shortcuts: no longer the home app, skipping %s", packageName)
            null
        } catch (e: IllegalStateException) {
            Timber.i(e, "Launcher shortcuts: user locked or unavailable for %s", packageName)
            null
        }
        return infos.orEmpty()
            .take(MAX_SHORTCUTS)
            .map { info -> toShortcut(service, packageName, info) }
    }

    /** Starts one shortcut; [sourceBounds] feeds the system launch animation. */
    fun start(packageName: String, shortcutId: String, sourceBounds: Rect?): Boolean {
        if (!isHostPermitted()) return false
        val service = launcherApps ?: return false
        return try {
            service.startShortcut(packageName, shortcutId, sourceBounds, null, Process.myUserHandle())
            true
        } catch (e: SecurityException) {
            Timber.i(e, "Launcher shortcuts: not allowed to start %s/%s", packageName, shortcutId)
            false
        } catch (e: IllegalStateException) {
            Timber.i(e, "Launcher shortcuts: %s/%s is not startable now", packageName, shortcutId)
            false
        } catch (e: ActivityNotFoundException) {
            Timber.i(e, "Launcher shortcuts: target of %s/%s is gone", packageName, shortcutId)
            false
        }
    }

    private fun toShortcut(
        service: LauncherApps,
        packageName: String,
        info: ShortcutInfo,
    ): AppShortcut = AppShortcut(
        id = info.id,
        packageName = packageName,
        label = (info.shortLabel?.toString()?.takeIf { it.isNotBlank() } ?: info.longLabel?.toString()).orEmpty(),
        icon = iconOf(service, info),
        isEnabled = info.isEnabled,
        disabledMessage = info.disabledMessage?.toString(),
    )

    private fun iconOf(service: LauncherApps, info: ShortcutInfo) = runCatching {
        service.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi)
    }.getOrElse { error ->
        Timber.i("Launcher shortcuts: icon of %s unavailable (%s)", info.id, error.javaClass.simpleName)
        null
    }

    private companion object {
        /** Publisher-side ceiling is per activity; five keeps the popup a glance, not a screen. */
        const val MAX_SHORTCUTS = 5
    }
}
