package com.sza.fastmediasorter.domain.usecase.launcher

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.repository.LauncherJournalRepository
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.streams.StreamsActivity
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
) {

    suspend fun launch(command: LauncherCellCommand): Boolean {
        val started = when (command) {
            is LauncherCellCommand.App -> launchPackage(command.packageName)
            is LauncherCellCommand.Feature -> launchFeature(command.routeKey)
            is LauncherCellCommand.Resource -> launchResource(command)
            is LauncherCellCommand.Stream -> launchStream(command.streamId)
            is LauncherCellCommand.OsShortcut -> launchOsShortcut(command.targetKey)
            // S1103: a scheduled op may modify or delete files, so it is confirmed then run from the
            // launcher UI path (ViewModel), never launched generically here.
            is LauncherCellCommand.ScheduledOp -> false
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

    private fun launchOsShortcut(targetKey: String): Boolean {
        val target = OsShortcutCatalog.byKey(targetKey) ?: return false
        if (!OsShortcutCatalog.isResolvable(context, targetKey)) {
            Timber.i("Launcher: system screen %s is absent on this device", targetKey)
            return false
        }
        return startIntent(target.intent(context))
    }

    private fun launchPackage(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Timber.i("Launcher: no launch intent for %s", packageName)
            return false
        }
        return startIntent(intent)
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
