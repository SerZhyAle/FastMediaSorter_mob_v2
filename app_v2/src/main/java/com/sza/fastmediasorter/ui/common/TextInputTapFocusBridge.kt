package com.sza.fastmediasorter.ui.common

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.google.android.material.textfield.TextInputEditText
import com.sza.fastmediasorter.core.compat.ChromeOsCompat

fun installTextInputTapFocusBridge(
    context: Context,
    container: View,
    editor: TextInputEditText,
) {
    val listener = View.OnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_UP && !editor.hasFocus()) {
            focusEditorFromTap(context, editor)
        }
        false
    }

    container.setOnTouchListener(listener)
    editor.setOnTouchListener(listener)
}

private fun focusEditorFromTap(context: Context, editor: TextInputEditText) {
    editor.requestFocusFromTouch()
    editor.requestFocus()
    editor.setSelection(editor.text?.length ?: 0)

    // Large outlined Material forms can miss the hand-off from box tap to inner editor focus.
    // Keep Chrome OS on the native click path and only add IME assist for other devices.
    if (ChromeOsCompat.isChromeOs(context)) return

    editor.post {
        if (!editor.isAttachedToWindow) return@post
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
    }
}