package com.sza.fastmediasorter.ui.calculator.helpers

import android.content.ClipboardManager
import android.content.Context
import com.sza.fastmediasorter.core.clipboard.copyTextToClipboard

/**
 * The calculator's only door to the system clipboard.
 *
 * S2024 gave copy and paste a second entry point - a tap and a long press on the result, beside the
 * menu rows that already existed - so the two system calls moved here rather than being repeated at
 * four call sites.
 *
 * S2192 moved the write half to the shared helper. Reading stays here: no other screen pastes from the
 * clipboard, so there is nothing to share.
 */
class CalculatorClipboardBridge(private val context: Context) {

    private val clipboard: ClipboardManager
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /** Copies the raw, ungrouped value - never the separator-formatted text drawn on screen. */
    fun copy(value: String) {
        context.copyTextToClipboard(CLIP_LABEL, value)
    }

    fun readText(): String =
        clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()

    private companion object {
        const val CLIP_LABEL = "calculator"
    }
}
