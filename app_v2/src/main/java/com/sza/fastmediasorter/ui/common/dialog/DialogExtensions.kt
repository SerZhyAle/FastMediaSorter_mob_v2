package com.sza.fastmediasorter.ui.common.dialog

import android.app.Dialog
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import timber.log.Timber

/**
 * Applies system-bar and display-cutout inset padding to a floating [Dialog]'s decor view
 * (S3242), so dialog content clears the status/navigation bars and any cutout without a
 * hand-rolled `OnApplyWindowInsetsListener` per dialog.
 */
fun Dialog.applyDialogInsets(
    applyLeft: Boolean = true,
    applyTop: Boolean = true,
    applyRight: Boolean = true,
    applyBottom: Boolean = true,
) {
    val decorView = window?.decorView ?: return
    Timber.d("S4835: dialog insets applied without the status-bar fallback")
    decorView.applySystemBarInsetPadding(
        applyLeft = applyLeft,
        applyTop = applyTop,
        applyRight = applyRight,
        applyBottom = applyBottom,
        // S4835: a centred floating window reads a 0 top inset because it does not overlap the status
        // bar; the fallback then guessed the bar height and drew an empty band above the dialog title.
        useStatusBarHeightFallback = false,
    )
}
