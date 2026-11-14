package com.sza.fastmediasorter.ui.notes.helpers

import android.content.Intent
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.notes.NoteSummary
import com.sza.fastmediasorter.ui.common.dialog.AppDialog
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * S4568: the per-row actions of the Notes list - share the text, or delete the note after a confirmation.
 */
class NotesRowActionManager(
    private val activity: AppCompatActivity,
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
        val text = try {
            File(note.path).readText(Charsets.UTF_8)
        } catch (e: IOException) {
            Timber.w(e, "NotesRowActionManager: note unreadable, sharing its caption only")
            note.caption
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, note.caption)
            .putExtra(Intent.EXTRA_TEXT, text)
        activity.startActivity(Intent.createChooser(send, activity.getString(R.string.notes_share)))
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
    }
}
