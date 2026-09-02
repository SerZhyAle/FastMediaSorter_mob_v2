package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.menu.MenuActionSurface
import com.sza.fastmediasorter.core.menu.StreamActionCatalog
import com.sza.fastmediasorter.core.menu.StreamMenuAction
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.usecase.streams.UpdateStreamSourceUseCase
import com.sza.fastmediasorter.ui.launcher.LauncherStreamEditDependencies
import com.sza.fastmediasorter.ui.streams.StreamEditDialog
import com.sza.fastmediasorter.ui.streams.StreamRemoveConfirmation
import com.sza.fastmediasorter.ui.streams.helpers.StreamShortcutPinManager
import kotlinx.coroutines.launch
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
    // S1500: the two use cases behind the shared edit dialog. Taken directly rather than as closures
    // like the rows above, because the streams screen backs that dialog with its own ViewModel and the
    // desktop's has nothing to add on the way through - a closure per callback would only be a longer
    // way to say the same thing.
    private val streamEdit: LauncherStreamEditDependencies,
    // S2247: the add flow owns the placement write; the callback keeps this manager free of the
    // desktop's view-model wiring while the menu still reaches it in one step.
    private val placeDesktopWindow: (identityKey: String, mediaKind: String, onResult: (Boolean) -> Unit) -> Unit,
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
     *
     * S1500 emptied the deferred set: every row the desktop surface offers now runs. The predicate
     * stays because the catalog asks it, and the next action to arrive unwired needs somewhere to say so.
     */
    fun supports(action: StreamMenuAction): Boolean = action !in DEFERRED

    private fun run(action: StreamMenuAction, source: StreamSourceEntity) {
        when (action) {
            StreamMenuAction.TOGGLE_PIN -> togglePin(source)
            StreamMenuAction.ADD_SHORTCUT -> pinShortcut(source)
            // S2247: one step from the channel's menu to a desktop window; the placement answers
            // asynchronously, so the result toast is spoken from the callback, not here.
            StreamMenuAction.ADD_DESKTOP_WINDOW -> {
                placeDesktopWindow(source.identityKey, source.mediaKind) { placed ->
                    val message = if (placed) {
                        R.string.launcher_stream_desktop_window_added
                    } else {
                        R.string.launcher_stream_desktop_window_no_space
                    }
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                }
            }
            StreamMenuAction.SHARE_LINK -> shareLink(source)
            StreamMenuAction.EDIT -> edit(source)
            StreamMenuAction.REMOVE -> confirmRemove(source)
            // Unreachable while [supports] and the desktop surface gate the rows; a warning rather
            // than silence, because a row that quietly does nothing is what this ticket removes.
            else -> Timber.w("Launcher offered channel action %s, which it cannot run", action)
        }
    }

    /**
     * S1500: the streams screen's own dialog, not a desktop copy of it - that copy is the divergence
     * strategic ADR-1 exists to prevent, and it is why this row was absent until the dialog moved out
     * of StreamsActivity.
     */
    private fun edit(source: StreamSourceEntity) {
        StreamEditDialog.show(
            activity = activity,
            source = source,
            callbacks = StreamEditDialog.Callbacks(
                readTrackPreference = { url -> streamEdit.streamTrackPreference.read(url) },
                writeTrackPreference = { url, audioIso, subtitleIso, subtitlesEnabled ->
                    activity.lifecycleScope.launch {
                        streamEdit.streamTrackPreference.writeAudio(url, audioIso)
                        streamEdit.streamTrackPreference.writeSubtitle(url, subtitleIso, subtitlesEnabled)
                    }
                },
                onEdit = { edited, url, title, kindOverride ->
                    activity.lifecycleScope.launch { persistEdit(edited, url, title, kindOverride) }
                },
            ),
        )
    }

    /**
     * A refusal has to be spoken here. On the streams screen a rejected url leaves the row visibly
     * unchanged in a list the user is looking at; on the desktop the dialog just closes and the cell
     * keeps its old label, which reads exactly like success.
     */
    private suspend fun persistEdit(
        source: StreamSourceEntity,
        url: String,
        title: String?,
        kindOverride: String?,
    ) {
        val message = when (streamEdit.updateStreamSource(source, url, title, kindOverride)) {
            UpdateStreamSourceUseCase.UpdateResult.InvalidUrl -> R.string.streams_error_invalid_url
            UpdateStreamSourceUseCase.UpdateResult.Duplicate -> R.string.streams_error_duplicate_url
            UpdateStreamSourceUseCase.UpdateResult.Success,
            UpdateStreamSourceUseCase.UpdateResult.NotEditable -> return
        }
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    /**
     * The icon is left to the platform: the favicon atlas the streams screen slices from belongs to
     * that screen's own loader, and a shortcut with the app's icon beats one that waits on an atlas
     * the desktop never loaded.
     */
    private fun pinShortcut(source: StreamSourceEntity) {
        // S1917: only the refusal is ours to report. A accepted request has not created anything yet -
        // the system asks the user next - so a success toast both lies and, on some devices, covers the
        // very dialog that decides it.
        if (!StreamShortcutPinManager(activity).requestPin(source)) {
            Toast.makeText(activity, R.string.streams_shortcut_unsupported, Toast.LENGTH_LONG).show()
        }
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
         * Actions the desktop cannot finish yet. Empty since S1500 moved the edit dialog out of
         * StreamsActivity into [StreamEditDialog], which both hosts reach; it had held EDIT for exactly
         * as long as that dialog had only one possible host.
         */
        val DEFERRED = emptySet<StreamMenuAction>()
    }
}
