package com.sza.fastmediasorter.ui.dialog

import android.content.Context
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.util.showBoundToHost

/**
 * S4037: the one renderer of a runtime SFTP host-key mismatch and the explicit confirmation behind
 * the re-pin action. Shows the pinned and the offered canonical fingerprints side by side and never
 * writes anything itself: the caller's [show] `onConfirmed` is the only path to a replaced pin
 * (SHARE-SESSION rule 7 - a mismatch refuses and is never auto-accepted).
 *
 * A half-informed dialog is a security defect, so [show] refuses to render unless both fingerprints
 * are present and the caller falls back to the static message.
 */
object HostKeyRepinDialog {

    /** True only when both canonical fingerprints exist; the caller keeps the static message otherwise. */
    fun canShow(expected: String?, actual: String?): Boolean =
        !expected.isNullOrBlank() && !actual.isNullOrBlank()

    /**
     * @param affectedCount resources that will take the new pin, named in the text before the decision.
     * @param onConfirmed the single path that may replace the stored pin.
     * @param onCancelled back, outside tap and the cancel button; a lifecycle teardown does not call it,
     * so the fallback rendering never runs against a destroyed host.
     * @return false when a fingerprint is missing and nothing was shown.
     */
    @Suppress("LongParameterList")
    fun show(
        context: Context,
        expected: String?,
        actual: String?,
        affectedCount: Int,
        onConfirmed: () -> Unit,
        onCancelled: () -> Unit = {}
    ): Boolean {
        if (expected.isNullOrBlank() || actual.isNullOrBlank()) return false
        val builder = MaterialAlertDialogBuilder(
            context,
            R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive
        )
        builder
            .setTitle(R.string.sftp_host_key_mismatch_title)
            .setView(buildBody(builder.context, expected, actual, affectedCount))
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
            .setPositiveButton(R.string.host_key_repin_confirm) { _, _ -> onConfirmed() }
            .setOnCancelListener { onCancelled() }
            .showBoundToHost(context)
        return true
    }

    // Selectable text so the user can copy a fingerprint and compare it outside the app. The pane title
    // is what TalkBack announces for the group; a content description on a selectable text would hide the
    // fingerprint itself from a screen reader.
    private fun buildBody(context: Context, expected: String, actual: String, affectedCount: Int): LinearLayout {
        val padding = context.resources.getDimensionPixelSize(R.dimen.dialog_padding_large)
        val gap = context.resources.getDimensionPixelSize(R.dimen.layout_spacing_large)
        val fingerprints = TextView(context).apply {
            text = context.getString(R.string.sftp_host_key_mismatch_body_format, expected, actual)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        val warning = TextView(context).apply {
            text = context.getString(R.string.host_key_repin_warning_format, affectedCount)
            setPadding(0, gap, 0, 0)
            setTextIsSelectable(true)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, gap, padding, 0)
            ViewCompat.setAccessibilityPaneTitle(
                this,
                context.getString(R.string.host_key_repin_fingerprints_description)
            )
            addView(fingerprints)
            addView(warning)
        }
    }
}
