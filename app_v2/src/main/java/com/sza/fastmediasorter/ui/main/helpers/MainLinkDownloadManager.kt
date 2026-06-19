package com.sza.fastmediasorter.ui.main.helpers

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.share.ReceiveShareActivity

/**
 * S0542: shows the "Link to download" single-line dialog (prefilled from the clipboard) and routes
 * the entered string into the existing external-link download path, without touching the download
 * engine, auth or progress UI directly.
 */
class MainLinkDownloadManager(private val activity: Activity) {

    fun show() {
        if (activity.isFinishing || activity.isDestroyed) return

        val input = EditText(activity).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = activity.getString(R.string.download_by_link_dialog_hint)
            setText(currentClipboardText())
            setSelection(text?.length ?: 0)
        }
        // Horizontal padding so the field is not flush against the dialog edges.
        val padding = (activity.resources.displayMetrics.density * 20).toInt()
        val container = FrameLayout(activity).apply { setPadding(padding, padding / 2, padding, 0) }
        container.addView(input)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.download_by_link_dialog_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val link = input.text?.toString()?.trim().orEmpty()
                if (link.isNotEmpty()) dispatchToReceiver(link)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun currentClipboardText(): String {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip?.takeIf { it.itemCount > 0 } ?: return ""
        return clip.getItemAt(0).coerceToText(activity)?.toString().orEmpty()
    }

    // Synthesize the same ACTION_SEND payload the system Share-sheet delivers, targeting the
    // existing receiver explicitly. This reuses the whole external-link pipeline (URL detection,
    // auth, download worker, progress/result) instead of duplicating any of it - strategic §5.
    private fun dispatchToReceiver(link: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, link)
            .setClass(activity, ReceiveShareActivity::class.java)
        activity.startActivity(intent)
    }
}
