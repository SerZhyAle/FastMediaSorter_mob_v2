package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.menu.MenuActionSurface
import com.sza.fastmediasorter.core.menu.StreamActionCatalog
import com.sza.fastmediasorter.core.menu.StreamMenuAction
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.ui.streams.StreamRemoveConfirmation
import com.sza.fastmediasorter.ui.streams.helpers.StreamShortcutPinManager
import timber.log.Timber

/**
 * S1424: carries out a channel's menu action from the launcher desktop, the counterpart of
 * [LauncherResourceActionManager] for the second cell kind the long press serves (strategic 2,
 * goal 2).
 *
 * Not injected: like its siblings it is built by its host and bound to that host's lifetime, so no
 * dialog or toast can outlive the surface that raised it.
 */
class LauncherStreamActionManager(
    private val activity: AppCompatActivity,
    private val loadStream: suspend (String) -> StreamSourceEntity?,
    private val loadPinnedStreams: suspend () -> List<StreamSourceEntity>,
    // No "play" row: the streams screen has none either - tapping the row or the cell plays it, and
    // a menu entry duplicating the tap would be the one row nobody needs.
    private val togglePin: (StreamSourceEntity) -> Unit,
    private val removeStream: (StreamSourceEntity) -> Unit,
) {

    /**
     * Menu rows for the channel behind [streamId], or an empty list when it is gone from the catalog -
     * a cell whose channel was removed elsewhere keeps behaving like a cell with nothing to expand.
     */
    suspend fun rowsFor(streamId: String): List<LauncherAppMenuRow> {
        val source = loadStream(streamId) ?: return emptyList()
        val facts = factsFor(source)
        return StreamActionCatalog
            .actionsFor(MenuActionSurface.LAUNCHER_DESKTOP, facts, ::supports)
            .map { action ->
                val label = activity.getString(StreamActionCatalog.labelRes(action, facts))
                LauncherAppMenuRow.Action(label, action.iconRes) { run(action, source) }
            }
    }

    /**
     * Whether this host can finish [action]. The catalog asks before offering a row, so an action
     * still waiting for its wiring is left out instead of shown dead (strategic ADR-2).
     */
    fun supports(action: StreamMenuAction): Boolean = action !in DEFERRED

    private fun run(action: StreamMenuAction, source: StreamSourceEntity) {
        when (action) {
            StreamMenuAction.TOGGLE_PIN -> togglePin(source)
            StreamMenuAction.ADD_SHORTCUT -> pinShortcut(source)
            StreamMenuAction.SHARE_LINK -> shareLink(source)
            StreamMenuAction.REMOVE -> confirmRemove(source)
            // Unreachable while [supports] and the desktop surface gate the rows; a warning rather
            // than silence, because a row that quietly does nothing is what this ticket removes.
            else -> Timber.w("Launcher offered channel action %s, which it cannot run", action)
        }
    }

    /**
     * The icon is left to the platform: the favicon atlas the streams screen slices from belongs to
     * that screen's own loader, and a shortcut with the app's icon beats one that waits on an atlas
     * the desktop never loaded.
     */
    private fun pinShortcut(source: StreamSourceEntity) {
        val requested = StreamShortcutPinManager(activity).requestPin(source)
        val message = if (requested) {
            R.string.streams_shortcut_created
        } else {
            R.string.streams_shortcut_unsupported
        }
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    /** The same chooser the streams screen raises, built from the same two extras. */
    private fun shareLink(source: StreamSourceEntity) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TEXT
            putExtra(Intent.EXTRA_TEXT, source.url)
        }
        val title = activity.getString(R.string.streams_share_chooser_title)
        activity.startActivity(Intent.createChooser(sendIntent, title))
    }

    private fun confirmRemove(source: StreamSourceEntity) {
        StreamRemoveConfirmation.show(activity, source.title) { removeStream(source) }
    }

    /**
     * `isReorderable` reads the pinned block even though the desktop drops every reorder row: the
     * facts describe the channel, not the menu, and letting the surface do the excluding is what
     * keeps one catalog answering both hosts (strategic 5.3).
     */
    private suspend fun factsFor(source: StreamSourceEntity) = StreamActionCatalog.Facts(
        isPinned = source.pinned,
        // Favourites are a streams-screen concept; the desktop drops that row outright (5.2), so
        // there is no state here worth reading.
        isFavorite = false,
        favoritesEnabled = false,
        isReorderable = source.pinned && loadPinnedStreams().size > 1,
        isManualOrigin = source.sourceOrigin == ORIGIN_MANUAL,
    )

    private companion object {

        const val MIME_TEXT = "text/plain"

        const val ORIGIN_MANUAL = "MANUAL"

        /**
         * Editing a channel opens the streams screen's own add/edit dialog, which carries the media-kind
         * override and the per-channel track preference and is built inside that Activity. Reproducing
         * it here is the divergence strategic ADR-1 exists to prevent, so the row stays absent until
         * that dialog has a home both hosts can reach.
         */
        val DEFERRED = setOf(StreamMenuAction.EDIT)
    }
}
