package com.sza.fastmediasorter.ui.notes.helpers

import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.usecase.notes.NoteSummary
import com.sza.fastmediasorter.ui.common.dialog.AppDialog
import com.sza.fastmediasorter.ui.share.SendToMenuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * S4568: the per-row actions of the Notes list - send the text through the Send to.. menu, or delete the
 * note after a confirmation.
 *
 * S4571: sending goes through the registry menu rather than a surface-local chooser, so a note reaches
 * Google Keep as the same «Keep: text» receiver every other text surface offers.
 */
class NotesRowActionManager(
    private val activity: AppCompatActivity,
    private val sendToMenu: SendToMenuManager,
    private val settings: suspend () -> AppSettings,
    private val onDeleteConfirmed: (NoteSummary) -> Unit,
) {

    fun show(note: NoteSummary, anchor: View) {
        val popup = PopupMenu(activity, anchor)
        popup.menu.add(0, ITEM_SHARE, 0, R.string.notes_share)
        popup.menu.add(0, ITEM_DELETE, 1, R.string.notes_delete)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ITEM_SHARE -> share(note)
                ITEM_DELETE -> confirmDelete(note)
            }
            true
        }
        popup.show()
    }

    private fun share(note: NoteSummary) {
        Timber.d("S4571: note Send to.. requested")
        activity.lifecycleScope.launch {
            val content = ShareableContent(
                uris = emptyList(),
                mime = TEXT_MIME,
                mediaType = MediaType.TEXT,
                text = readBody(note),
                displayName = note.caption,
            )
            sendToMenu.show(activity, content, settings())
        }
    }

    private suspend fun readBody(note: NoteSummary): String = withContext(Dispatchers.IO) {
        try {
            File(note.path).readText(Charsets.UTF_8)
        } catch (e: IOException) {
            Timber.w(e, "NotesRowActionManager: note unreadable, sending its caption only")
            note.caption
        }
    }

    private fun confirmDelete(note: NoteSummary) {
        AppDialog.destructive(
            owner = activity,
            context = activity,
            title = activity.getString(R.string.notes_delete_title),
            message = activity.getString(R.string.notes_delete_confirm, note.caption),
            confirmLabel = activity.getString(R.string.notes_delete),
            onConfirm = { onDeleteConfirmed(note) },
        )
    }

    private companion object {
        const val ITEM_SHARE = 1
        const val ITEM_DELETE = 2
        const val TEXT_MIME = "text/plain"
    }
}
