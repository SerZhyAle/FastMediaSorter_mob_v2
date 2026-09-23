package com.sza.fastmediasorter.ui.common

import android.view.View
import android.view.inputmethod.InputMethodManager

/**
 * Asks the IME to show the keyboard for [view] as an implicit request.
 *
 * The flag is deprecated with no platform replacement, and passing `0` instead is not equivalent:
 * an explicit request forces the soft keyboard up even when a hardware keyboard is attached, which
 * is the TV, Chrome OS and keyboard-driven setup this app supports. Keeping the flag in one place
 * keeps that decision reviewable instead of re-made at every call site.
 */
@Suppress("DEPRECATION")
fun InputMethodManager.showSoftInputImplicitly(view: View): Boolean {
    return showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}
