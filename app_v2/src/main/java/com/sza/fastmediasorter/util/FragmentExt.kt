package com.sza.fastmediasorter.util

import android.app.Dialog
import android.view.WindowManager
import androidx.fragment.app.Fragment
import timber.log.Timber

/**
 * Safely show a dialog from a Fragment context.
 * Guards against WindowLeaked when the Fragment is detached or Activity is finishing/destroyed.
 */
fun Fragment.safeShowDialog(buildDialog: () -> Dialog) {
    if (!isAdded || activity?.isFinishing == true || activity?.isDestroyed == true) {
        Timber.w("safeShowDialog: skipping — fragment not attached or Activity finishing (${this::class.simpleName})")
        return
    }
    try {
        buildDialog().show()
    } catch (e: WindowManager.BadTokenException) {
        Timber.e(e, "safeShowDialog: show failed — bad window token (${this::class.simpleName})")
    }
}

/**
 * Check if a Fragment is safe to show dialogs.
 */
fun Fragment.canShowDialog(): Boolean =
    isAdded && activity?.isFinishing == false && activity?.isDestroyed == false
