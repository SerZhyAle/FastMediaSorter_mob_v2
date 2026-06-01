package com.sza.fastmediasorter.ui.player.helpers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.calculator.CalculatorActivity
import timber.log.Timber

/**
 * Unified floating ActionMode callback for document text selection (TXT, EPUB, PDF overlay).
 *
 * Adds "Translate", "Search in Google", "Calculator", and optionally "Read Aloud" items on top
 * of the platform-provided "Copy / Share / Select All" items.  Platform items are NOT touched - they
 * are injected automatically by the system.
 *
 * @param showTranslate    Whether to show the Translate item (BuildConfig.ENABLE_TRANSLATION AND
 *                         user setting).  Pass false to hide the item completely.
 * @param showReadAloud    Whether to show the Read Aloud item.  Pass false to hide it.
 * @param getSelectedText  Synchronous supplier of the currently selected text.  Called on the
 *                         main thread when the user taps an action item.
 * @param onTranslate      Called with the selected text when the user taps "Translate".
 * @param onSearchGoogle   Called with the selected text when the user taps "Search in Google".
 * @param isCalculatorAvailable Whether to show the calculator item for this ActionMode instance.
 * @param onOpenCalculator Called with the selected text when the user taps "Calculator".
 * @param onReadAloud      Called with the selected text when the user taps "Read Aloud".
 *                         Null means the item will not be shown regardless of [showReadAloud].
 */
class DocumentSelectionActionModeCallback(
    private val showTranslate: Boolean,
    private val showReadAloud: Boolean = false,
    private val getSelectedText: () -> String,
    private val onTranslate: (String) -> Unit,
    private val onSearchGoogle: (String) -> Unit,
    private val isCalculatorAvailable: () -> Boolean = { false },
    private val onOpenCalculator: ((String) -> Unit)? = null,
    private val onReadAloud: ((String) -> Unit)? = null
) : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.document_selection_menu, menu)
        menu.findItem(R.id.action_translate_selection)?.isVisible = showTranslate
        menu.findItem(R.id.action_open_calculator_selection)?.isVisible =
            isCalculatorAvailable() && onOpenCalculator != null
        menu.findItem(R.id.action_read_aloud_selection)?.isVisible = showReadAloud && onReadAloud != null
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
                Timber.d("DocumentSelectionActionModeCallback: translate '${text.take(40)}..'")
                onTranslate(text)
                mode.finish()
                true
            }
            R.id.action_search_google -> {
                Timber.d("DocumentSelectionActionModeCallback: search google '${text.take(40)}..'")
                onSearchGoogle(text)
                mode.finish()
                true
            }
            R.id.action_open_calculator_selection -> {
                val callback = onOpenCalculator ?: return false
                Timber.d("DocumentSelectionActionModeCallback: calculator '${text.take(40)}..'")
                callback(text)
                mode.finish()
                true
            }
            R.id.action_read_aloud_selection -> {
                Timber.d("DocumentSelectionActionModeCallback: read aloud '${text.take(40)}..'")
                onReadAloud?.invoke(text)
                mode.finish()
                true
            }
            else -> false
        }
    }

    override fun onDestroyActionMode(mode: ActionMode) {}
}

/**
 * Wraps a platform WebView floating ActionMode callback and appends document actions.
 *
 * WebView does not support TextView's custom selection callback API, so host activities
 * intercept `startActionMode()` and use this wrapper for document WebView surfaces.
 */
class DocumentSelectionActionModeAugmentingCallback(
    private val original: ActionMode.Callback,
    private val document: DocumentSelectionActionModeCallback,
) : ActionMode.Callback2() {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        document.onCreateActionMode(mode, menu)
        return original.onCreateActionMode(mode, menu)
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
        original.onPrepareActionMode(mode, menu)

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
        document.onActionItemClicked(mode, item) || original.onActionItemClicked(mode, item)

    override fun onDestroyActionMode(mode: ActionMode) {
        original.onDestroyActionMode(mode)
    }

    override fun onGetContentRect(mode: ActionMode, view: android.view.View, outRect: android.graphics.Rect) {
        if (original is ActionMode.Callback2) {
            original.onGetContentRect(mode, view, outRect)
        } else {
            super.onGetContentRect(mode, view, outRect)
        }
    }
}

/**
 * Opens the device's default browser with a Google search for [text].
 */
fun openGoogleSearch(context: Context, text: String) {
    val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(text)}")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Timber.w("openGoogleSearch: no browser available - %s", e.message)
    }
}

/**
 * Opens the embedded calculator with [text] as initial input without requesting a result.
 */
fun openCalculatorForSelection(context: Context, text: String) {
    try {
        context.startActivity(
            CalculatorActivity.createIntent(
                context = context,
                initialInput = text,
                returnResult = false,
            )
        )
    } catch (e: ActivityNotFoundException) {
        Timber.w("openCalculatorForSelection: calculator unavailable - %s", e.message)
    }
}
