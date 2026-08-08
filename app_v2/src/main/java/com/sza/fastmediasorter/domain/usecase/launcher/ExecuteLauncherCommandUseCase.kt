package com.sza.fastmediasorter.domain.usecase.launcher

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.data.launcher.AppShortcutDataSource
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactTarget
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.repository.LauncherJournalRepository
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import com.sza.fastmediasorter.domain.usecase.radio.ToggleRadioTargetUseCase
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.streams.StreamsActivity
import com.sza.fastmediasorter.util.resolveActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S0404: runs the command stored behind a launcher cell, taskbar pin or Start-menu row, and records
 * the launch in the app's own journal (ADR-7). Returns whether an activity was started, so the
 * caller can tell the user when a target is gone instead of failing silently.
 */
class ExecuteLauncherCommandUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolveRouteAvailability: ResolvePanelRouteAvailabilityUseCase,
    private val streamSourceRepository: StreamSourceRepository,
    private val journal: LauncherJournalRepository,
    private val appShortcutDataSource: AppShortcutDataSource,
    private val toggleRadioTarget: ToggleRadioTargetUseCase,
) {

    suspend fun launch(command: LauncherCellCommand): Boolean {
        Timber.d("S1435: launcher command funnel entered, command=%s", command)
        val started = when (command) {
            is LauncherCellCommand.App -> launchPackage(command.packageName)
            is LauncherCellCommand.Feature -> launchFeature(command.routeKey)
            is LauncherCellCommand.Resource -> launchResource(command)
            is LauncherCellCommand.Stream -> launchStream(command.streamId)
            is LauncherCellCommand.OsShortcut -> launchOsShortcut(command.targetKey)
            // S1170: mirrors the favourites widget's per-row fill-in intent - the file's own resource,
            // opened at that file. skipAvailabilityCheck matches the widget: the row was listed from the
            // favourites table a moment ago, so a second existence probe only delays the open.
            is LauncherCellCommand.FavoriteFile -> startIntent(favoriteFileIntent(command))
            is LauncherCellCommand.Contact -> launchContact(command.target)
            is LauncherCellCommand.PinnedShortcut -> launchPinnedShortcut(command)
            // S1103: a scheduled op may modify or delete files, so it is confirmed then run from the
            // launcher UI path (ViewModel), never launched generically here.
            is LauncherCellCommand.ScheduledOp -> false
            // S1402: editing the desktop and leaving launcher mode only exist inside a running launcher,
            // so there is no Intent to start - the host intercepts this kind before it ever reaches here.
            is LauncherCellCommand.LauncherAction -> false
            // S1428: a section header is a caption, not a target. Its tap collapses its own rows inside
            // the running launcher, so like the two above it never reaches an Intent - and returning
            // false here also keeps it out of the launch journal, which records openings.
            is LauncherCellCommand.Section -> false
        }
        if (started) journal.record(command)
        return started
    }

    private suspend fun launchFeature(routeKey: String): Boolean {
        val route = InternalRouteCatalog.byKey(routeKey) ?: return false
        val availability = resolveRouteAvailability(routeKey)
        return when {
            availability.isLaunchable -> startIntent(route.intent(context))
            // Compiled in but switched off: open the setting that controls it rather than dead-launch.
            availability.availableInBuild ->
                route.settingsIntent?.let { startIntent(it(context)) } ?: false
            else -> false
        }
    }

    private fun launchResource(command: LauncherCellCommand.Resource): Boolean {
        val intent = when (command.mode) {
            LauncherResourceMode.BROWSE -> BrowseActivity.createIntent(context, command.resourceId)
            LauncherResourceMode.SLIDESHOW -> PlayerActivity.createPanelIntent(
                context = context,
                resourceId = command.resourceId,
                isSlideshowEnabled = true,
            )
            LauncherResourceMode.PLAY -> PlayerActivity.createPanelIntent(
                context = context,
                resourceId = command.resourceId,
            )
        }
        return startIntent(intent)
    }

    private suspend fun launchStream(streamId: String): Boolean {
        val source = streamSourceRepository.getById(streamId)
        if (source == null) {
            Timber.i("Launcher: stream %s is no longer in the catalog", streamId)
            return false
        }
        return startIntent(StreamsActivity.createPlayIntent(context, source.url))
    }

    private suspend fun launchOsShortcut(targetKey: String): Boolean {
        val target = OsShortcutCatalog.byKey(targetKey)
            ?.takeIf { OsShortcutCatalog.isResolvable(context, targetKey) }
        if (target == null) {
            Timber.i("Launcher: system screen %s is unknown to this build or absent here", targetKey)
            return false
        }
        // S1441: a radio target tries to switch itself first; only a refusal reaches a system screen.
        return toggleRadioTarget(target) || startIntent(osShortcutIntent(target))
    }

    /**
     * S1441: the surface a refused toggle opens - the system panel when the target has one and it resolves
     * here, otherwise the full settings screen every target has always used.
     */
    private fun osShortcutIntent(target: OsShortcutCatalog.Target): Intent {
        val fallback = target.fallbackIntent?.invoke(context)
        val resolves = fallback != null && context.packageManager.resolveActivityCompat(fallback, 0) != null
        return if (resolves) requireNotNull(fallback) else target.intent(context)
    }

    /**
     * S1176: the four contact outcomes, each resolved before it is started.
     *
     * Nothing here needs a permission: every target was captured under the one-time grant the system
     * picker gave on the picked record. `DIAL` opens the dialler pre-filled and `SMS` opens the
     * messaging app pre-addressed rather than acting (ADR-3) - doing either would need `CALL_PHONE` or
     * `SEND_SMS`, and both stay undeclared.
     *
     * Nothing about the person reaches the log. The name, the number and the lookup key are all user
     * data that would otherwise sit in a bug report; the action kind is enough to diagnose a dead cell.
     */
    private fun launchContact(target: LauncherContactTarget): Boolean {
        val intent = contactIntent(target)
            ?.takeIf { context.packageManager.resolveActivityCompat(it) != null }
        if (intent == null) {
            // One message for both causes on purpose: from the user's side "the snapshot is incomplete"
            // and "no app handles this" are the same dead cell, and the action kind is all a bug report
            // needs. The name, number and lookup key stay out of the log.
            Timber.i("Launcher: contact action %s cannot run on this device", target.action.name)
            return false
        }
        val started = startIntent(intent)
        Timber.d("S1176: run action=%s started=%b", target.action.name, started)
        return started
    }

    private fun contactIntent(target: LauncherContactTarget): Intent? {
        if (!target.isUsable) return null
        return when (target.action) {
            LauncherContactAction.PROFILE -> Intent(
                Intent.ACTION_VIEW,
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, target.lookupKey),
            )
            LauncherContactAction.DIAL -> Intent(
                Intent.ACTION_DIAL,
                Uri.fromParts(TEL_SCHEME, target.phoneNumber, null),
            )
            // The number rides in the URI, not in an extra: some handlers ignore EXTRA_PHONE_NUMBER
            // and open an empty compose screen instead (strategic §8).
            LauncherContactAction.SMS -> Intent(
                Intent.ACTION_SENDTO,
                Uri.fromParts(SMS_SCHEME, target.phoneNumber, null),
            )
            // The picked row addressed through the app that registered it: a messaging data row is
            // meaningless to any other handler, so the package is part of the target, not a hint.
            LauncherContactAction.MESSAGE -> Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, target.messageDataId),
                    null,
                )
                setPackage(target.messagePackage)
            }
        }
    }

    private fun favoriteFileIntent(command: LauncherCellCommand.FavoriteFile): Intent {
        Timber.d("S1170: favourite row opens resource %d", command.resourceId)
        return PlayerActivity.createPanelIntent(
            context = context,
            resourceId = command.resourceId,
            skipAvailabilityCheck = true,
            initialFilePath = command.filePath,
        )
    }

    /**
     * S1205: started by identifier, never by intent - a pinned shortcut's own intent is readable only
     * by the platform, which is exactly why it was never copied into the cell.
     */
    private fun launchPinnedShortcut(command: LauncherCellCommand.PinnedShortcut): Boolean =
        appShortcutDataSource.start(command.packageName, command.shortcutId, sourceBounds = null)

    private fun launchPackage(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Timber.i("Launcher: no launch intent for %s", packageName)
            return false
        }
        return startIntent(intent)
    }

    private companion object {
        const val TEL_SCHEME = "tel"
        const val SMS_SCHEME = "smsto"
    }

    private fun startIntent(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            Timber.w(it, "Launcher: failed to launch %s", intent.component ?: intent.action)
            false
        }
    }
}
