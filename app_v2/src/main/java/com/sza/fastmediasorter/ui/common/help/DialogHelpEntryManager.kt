package com.sza.fastmediasorter.ui.common.help

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.IdRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ViewDialogHelpButtonBinding
import com.sza.fastmediasorter.ui.common.input.UiSurface
import timber.log.Timber

/**
 * The help of a dialog (contract `CONTEXT-HELP` 0.2 items A and E): F1 and the "?" of the title row
 * open the help the map gives the dialog's own surface id - the parent's page for an `inherit` row.
 *
 * A dialog the map does not name gets neither. The host screen's page is never borrowed as a fallback:
 * a confirmation is `none` by rule 4, and the key and the button must give the same answer (rule 3).
 */
object DialogHelpEntryManager {

    /** [explicitId] when given, else the class name of the [DialogFragment] that owns [dialog], else null. */
    fun surfaceIdOf(dialog: Dialog, explicitId: String?): String? =
        explicitId ?: activityOf(dialog.context)?.supportFragmentManager
            ?.let { ownerOf(dialog, it) }?.javaClass?.simpleName

    /** Opens the help of [surfaceId] and returns true, or returns false and opens nothing. */
    fun openHelp(dialog: Dialog, surfaceId: String?): Boolean {
        val activity = activityOf(dialog.context)
        if (surfaceId == null || activity == null) return false
        Timber.d("S4660: dialog help open surface=$surfaceId")
        return HelpOpenManager.openIfPresent(activity, surfaceId, UiSurface.DIALOG)
    }

    /**
     * Shows the "?" of a shown [dialog] when [surfaceId] has a page: the `btnDialogHelp` slot of the
     * dialog's own layout, else a button appended to the platform title row of a titled builder dialog.
     */
    fun installButton(dialog: Dialog, surfaceId: String?) {
        val decor = dialog.window?.peekDecorView() ?: return
        val available = surfaceId != null && HelpOpenManager.hasHelp(dialog.context, surfaceId)
        val button = decor.findHostView(R.id.btnDialogHelp) ?: if (available) appendToTitleRow(decor) else null
        button ?: return
        Timber.d("S4666: help slot surface=$surfaceId available=$available")
        Timber.d("S4660: dialog help button surface=$surfaceId visible=$available")
        button.visibility = if (available) View.VISIBLE else View.GONE
        val open = debouncedOpener(dialog, surfaceId)
        button.setOnClickListener { open() }
    }

    /**
     * [openHelp] behind a short double-tap guard: the page opens over the app, so a second tap on the
     * button would open it twice. [installButton] uses it for the XML button; a dialog that draws its own
     * control, such as a Compose card, takes it directly.
     */
    fun debouncedOpener(dialog: Dialog, surfaceId: String?): () -> Unit {
        var lastOpenAt = 0L
        return {
            val now = SystemClock.uptimeMillis()
            if (now - lastOpenAt >= DOUBLE_TAP_WINDOW_MS) {
                lastOpenAt = now
                openHelp(dialog, surfaceId)
            }
        }
    }

    /**
     * For a shown dialog that does not use the keyboard delegate: the button plus an F1-only key listener.
     * It replaces any key listener the dialog had, so a dialog with its own keys uses the delegate instead.
     */
    fun attach(dialog: Dialog, surfaceId: String?) {
        installButton(dialog, surfaceId)
        if (surfaceId == null || !HelpOpenManager.hasHelp(dialog.context, surfaceId)) return
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_F1 && event.action == KeyEvent.ACTION_UP) openHelp(dialog, surfaceId)
            keyCode == KeyEvent.KEYCODE_F1
        }
    }

    /** Dialog contexts are usually theme wrappers around the activity, never the activity itself. */
    fun activityOf(context: Context): FragmentActivity? {
        var current: Context? = context
        while (current != null && current !is FragmentActivity) {
            current = (current as? ContextWrapper)?.baseContext
        }
        return current as? FragmentActivity
    }

    private fun ownerOf(dialog: Dialog, manager: FragmentManager): DialogFragment? =
        manager.fragments.firstNotNullOfOrNull { fragment ->
            (fragment as? DialogFragment)?.takeIf { it.dialog === dialog }
                ?: ownerOf(dialog, fragment.childFragmentManager)
        }

    // The platform title row is a horizontal LinearLayout whose title is match_parent: without a weight
    // the appended button would get no width.
    private fun appendToTitleRow(decor: View): View? {
        val row = decor.findHostView(androidx.appcompat.R.id.title_template) as? LinearLayout
        val usable = row != null && row.orientation == LinearLayout.HORIZONTAL && row.isTitleShown()
        if (!usable) return null
        row.findHostView(androidx.appcompat.R.id.alertTitle)?.let { title ->
            title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        return ViewDialogHelpButtonBinding.inflate(LayoutInflater.from(row.context), row, true).root
    }

    /**
     * Every id this object looks up lives in a layout it does not own - the platform title row, or the
     * help slot of whichever dialog layout is showing - so no generated binding can reach it (S1693).
     */
    private fun View.findHostView(@IdRes id: Int): View? = findViewById(id)

    private fun View.isTitleShown(): Boolean =
        visibility == View.VISIBLE && (parent as? View)?.visibility == View.VISIBLE

    private const val DOUBLE_TAP_WINDOW_MS = 600L
}
