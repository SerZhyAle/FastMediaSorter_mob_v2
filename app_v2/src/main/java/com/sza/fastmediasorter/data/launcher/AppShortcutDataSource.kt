package com.sza.fastmediasorter.data.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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

    /**
     * S1205: the pin request another app addressed to the default launcher, or null when this one is
     * not ours to take.
     *
     * Only a shortcut request qualifies. A widget request arrives through the same action and is a
     * different feature entirely, and a request from another profile could be accepted but never
     * started - every launch on this seam runs as [Process.myUserHandle].
     */
    fun pinRequestFrom(intent: Intent): LauncherApps.PinItemRequest? {
        val service = launcherApps ?: return null
        val request = try {
            service.getPinItemRequest(intent)
        } catch (e: SecurityException) {
            Timber.i(e, "Launcher pin: not allowed to read the pin request")
            null
        }
        return request
            ?.takeIf { it.isValid }
            ?.takeIf { it.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT }
            ?.takeIf { it.shortcutInfo?.userHandle == Process.myUserHandle() }
    }

    /**
     * S1205: takes the pin request and returns what was pinned, or null when the system refused it.
     *
     * The snapshot is read after [LauncherApps.PinItemRequest.accept] rather than before, because the
     * label and the icon are what the desktop cell will carry and only an accepted request is
     * guaranteed to still describe a shortcut the launcher may query.
     */
    fun acceptPinRequest(request: LauncherApps.PinItemRequest): AppShortcut? {
        val service = launcherApps ?: return null
        val accepted = try {
            request.accept()
        } catch (e: SecurityException) {
            Timber.i(e, "Launcher pin: not allowed to accept the request")
            false
        } catch (e: IllegalStateException) {
            Timber.i(e, "Launcher pin: request is no longer acceptable")
            false
        }
        return request.shortcutInfo
            ?.takeIf { accepted }
            ?.let { info -> toShortcut(service, info.`package`, info) }
    }

    /**
     * S1205: the pinned shortcut behind a desktop cell while it is still alive, or null once it is
     * not - uninstalled, unpublished, or disabled by its owner.
     *
     * A disabled match counts as gone on purpose: the publisher kept the record so pinned copies keep
     * a caption, but the shortcut no longer does anything, which is the same dead cell to the user.
     */
    fun pinned(packageName: String, shortcutId: String): AppShortcut? {
        val service = launcherApps?.takeIf { isHostPermitted() } ?: return null
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setShortcutIds(listOf(shortcutId))
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        val infos = try {
            service.getShortcuts(query, Process.myUserHandle())
        } catch (e: SecurityException) {
            Timber.i(e, "Launcher pin: no longer the home app, cannot resolve %s", packageName)
            null
        } catch (e: IllegalStateException) {
            Timber.i(e, "Launcher pin: user locked or unavailable for %s", packageName)
            null
        }
        return infos?.firstOrNull()
            ?.takeIf { it.isEnabled }
            ?.let { info -> toShortcut(service, packageName, info) }
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
