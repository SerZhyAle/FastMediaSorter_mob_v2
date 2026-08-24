package com.sza.fastmediasorter.domain.usecase.launcher

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.sza.fastmediasorter.core.panel.AppLaunchPanelRouteIntents
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.data.launcher.AppShortcutDataSource
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactTarget
import com.sza.fastmediasorter.domain.model.launcher.LauncherGeographicAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.repository.LauncherJournalRepository
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import com.sza.fastmediasorter.domain.usecase.radio.ToggleRadioTargetUseCase
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.util.resolveActivityCompat
import com.sza.fastmediasorter.widget.StreamPlayLaunchActivity
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

    suspend fun launch(command: LauncherCellCommand, screenOnly: Boolean = false): Boolean {
        val started = when (command) {
            is LauncherCellCommand.App -> launchPackage(command.packageName)
            is LauncherCellCommand.Feature -> launchFeature(command.routeKey)
            is LauncherCellCommand.FeatureSection -> launchFeatureSection(command)
            is LauncherCellCommand.Resource -> launchResource(command)
            is LauncherCellCommand.Stream -> launchStream(command.identityKey)
            is LauncherCellCommand.OsShortcut -> launchOsShortcut(command.targetKey, screenOnly)
            // S1170: mirrors the favourites widget's per-row fill-in intent - the file's own resource,
            // opened at that file. skipAvailabilityCheck matches the widget: the row was listed from the
            // favourites table a moment ago, so a second existence probe only delays the open.
            is LauncherCellCommand.FavoriteFile -> startIntent(favoriteFileIntent(command))
            is LauncherCellCommand.Contact -> launchContact(command.target)
            is LauncherCellCommand.PinnedShortcut -> launchPinnedShortcut(command)
            is LauncherCellCommand.Geographic -> startIntent(geographicIntent(command))
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

    /**
     * S1440: the Monitor is the only route with addressable sub-screens today, so an unknown route here
     * declines rather than opening something the caller did not ask for.
     */
    private suspend fun launchFeatureSection(command: LauncherCellCommand.FeatureSection): Boolean {
        val availability = resolveRouteAvailability(command.routeKey)
        val isMonitor = command.routeKey == InternalRouteCatalog.KEY_NETWORK_MONITOR
        return when {
            !isMonitor -> false
            availability.isLaunchable ->
                startIntent(AppLaunchPanelRouteIntents.networkMonitor(context, command.sectionKey))
            availability.availableInBuild ->
                startIntent(AppLaunchPanelRouteIntents.networkMonitorSettings(context))
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

    // S1832: [cellKey] is the channel's identity, or a row id for a cell written before that ticket -
    // the repository resolves both, so nothing here has to know which form it was handed.
    private suspend fun launchStream(cellKey: String): Boolean {
        val source = streamSourceRepository.getByIdentityOrId(cellKey)
        if (source == null) {
            Timber.i("Launcher: stream %s is no longer in the catalog", cellKey)
            return false
        }
        // S1471: the trampoline, not the Streams screen - this one command backs both the launcher
        // desktop tile and the Streams gadget row, so both surfaces get the screen-less start.
        return startIntent(StreamPlayLaunchActivity.createIntent(context, source.url))
    }

    /**
     * @param screenOnly S1767: open the target's settings screen and do nothing else.
     *
     * A desktop cell the user placed as a switch wants the radio attempt below; a status indicator does
     * not. Tapping the Wi-Fi indicator to have Wi-Fi switch off is the opposite of what an indicator's
     * tap promises, and the panel [osShortcutIntent] prefers is a toggle rather than the settings section
     * that ticket's §11 asks for - so a screen-only caller skips both.
     */
    private suspend fun launchOsShortcut(targetKey: String, screenOnly: Boolean): Boolean {
        val target = OsShortcutCatalog.byKey(targetKey)
            ?.takeIf { OsShortcutCatalog.isResolvable(context, targetKey) }
        if (target == null) {
            Timber.i("Launcher: system screen %s is unknown to this build or absent here", targetKey)
            return false
        }
        // S1441: a radio target tries to switch itself first; only a refusal reaches a system screen.
        // Written as one expression rather than an early return: a third return crosses detekt's limit.
        return if (screenOnly) {
            startIntent(target.intent(context))
        } else {
            toggleRadioTarget(target) || startIntent(osShortcutIntent(target))
        }
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

    /** Uses only documented Maps URLs and intents; no Maps-internal activity names are relied upon. */
    private fun geographicIntent(command: LauncherCellCommand.Geographic): Intent = when (command.action) {
        LauncherGeographicAction.DIRECTIONS -> Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(command.query)}"),
        ).setPackage(GOOGLE_MAPS_PACKAGE)

        LauncherGeographicAction.NAVIGATION -> Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=${Uri.encode(command.query)}"),
        ).setPackage(GOOGLE_MAPS_PACKAGE)

        LauncherGeographicAction.SHOW_PLACE -> showPlaceIntent(command)
    }

    /**
     * S1616: a stored link is opened as a link, a stored place name as a search.
     *
     * `geo:0,0?q=` means "look this text up", so the link a share keeps when it cannot be resolved to
     * coordinates (S1585 §6.1) used to open a search for its own URL. Maps parses its own links; when
     * Maps is absent the same view intent still reaches a browser instead of failing to start.
     */
    private fun showPlaceIntent(command: LauncherCellCommand.Geographic): Intent {
        if (!command.isWebLinkQuery) {
            return Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(command.query)}"))
        }
        val viaMaps = Intent(Intent.ACTION_VIEW, Uri.parse(command.query)).setPackage(GOOGLE_MAPS_PACKAGE)
        val mapsHandles = context.packageManager.resolveActivityCompat(viaMaps) != null
        return if (mapsHandles) viaMaps else Intent(Intent.ACTION_VIEW, Uri.parse(command.query))
    }

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
        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
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
