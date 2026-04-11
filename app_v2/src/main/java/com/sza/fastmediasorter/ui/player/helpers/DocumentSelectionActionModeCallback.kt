package com.sza.fastmediasorter.ui.player.helpers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * Unified floating ActionMode callback for document text selection (TXT, EPUB, PDF overlay).
 *
 * Adds "Translate" and "Search in Google" items on top of the platform-provided
 * "Copy / Share / Select All" items.  Platform items are NOT touched — they are
 * injected automatically by the system.
 *
 * @param showTranslate    Whether to show the Translate item (BuildConfig.ENABLE_TRANSLATION AND
 *                         user setting).  Pass false to hide the item completely.
 * @param getSelectedText  Synchronous supplier of the currently selected text.  Called on the
 *                         main thread when the user taps an action item.
 * @param onTranslate      Called with the selected text when the user taps "Translate".
 * @param onSearchGoogle   Called with the selected text when the user taps "Search in Google".
 */
class DocumentSelectionActionModeCallback(
    private val showTranslate: Boolean,
    private val getSelectedText: () -> String,
    private val onTranslate: (String) -> Unit,
    private val onSearchGoogle: (String) -> Unit
) : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.document_selection_menu, menu)
        menu.findItem(R.id.action_translate_selection)?.isVisible = showTranslate
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val text = getSelectedText()
        if (text.isBlank()) {
            Timber.w("DocumentSelectionActionModeCallback: selected text is blank, ignoring action")
            return false
        }
        return when (item.itemId) {
            R.id.action_translate_selection -> {
                Timber.d("DocumentSelectionActionModeCallback: translate '${text.take(40)}…'")
                onTranslate(text)
                mode.finish()
                true
            }
            R.id.action_search_google -> {
                Timber.d("DocumentSelectionActionModeCallback: search google '${text.take(40)}…'")
                onSearchGoogle(text)
                mode.finish()
                true
            }
            else -> false
        }
    }

    override fun onDestroyActionMode(mode: ActionMode) {}
}

/**
 * Opens the device's default browser with a Google search for [text].
 */
fun openGoogleSearch(context: Context, text: String) {
    val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(text)}")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Timber.w("openGoogleSearch: no browser available — %s", e.message)
    }
}
