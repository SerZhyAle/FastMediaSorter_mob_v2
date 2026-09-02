package com.sza.fastmediasorter.ui.networkmonitor.helpers

import android.content.Context
import androidx.annotation.StringRes
import com.sza.fastmediasorter.core.clipboard.copyTextToClipboard

/**
 * S2025: copies one Monitor value to the clipboard and confirms it.
 *
 * A file of view-side extensions rather than a class, matching [startFirstAvailableSystemSurface] beside it:
 * the two Summary address rows need identical behaviour.
 *
 * S2192 moved the clipboard write and the Android 13+ confirmation rule into the shared helper, so this
 * function now only resolves the label resource the Monitor rows pass and forwards.
 */
internal fun Context.copyMonitorValue(@StringRes labelRes: Int, value: String) {
    copyTextToClipboard(getString(labelRes), value)
}
