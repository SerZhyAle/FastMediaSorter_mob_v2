package com.sza.fastmediasorter.ui.common.dialog

import android.app.Dialog
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding

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
    decorView.applySystemBarInsetPadding(
        applyLeft = applyLeft,
        applyTop = applyTop,
        applyRight = applyRight,
        applyBottom = applyBottom,
    )
}
