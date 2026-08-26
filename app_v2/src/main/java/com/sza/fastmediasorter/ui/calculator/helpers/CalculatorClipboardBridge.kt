package com.sza.fastmediasorter.ui.calculator.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.sza.fastmediasorter.R

/**
 * The calculator's only door to the system clipboard.
 *
 * S2024 gave copy and paste a second entry point - a tap and a long press on the result, beside the
 * menu rows that already existed - so the two system calls moved here rather than being repeated at
 * four call sites.
 */
class CalculatorClipboardBridge(private val context: Context) {

    private val clipboard: ClipboardManager
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /** Copies the raw, ungrouped value - never the separator-formatted text drawn on screen. */
    fun copy(value: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, value))
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    fun readText(): String =
        clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()

    private companion object {
        const val CLIP_LABEL = "calculator"
    }
}
