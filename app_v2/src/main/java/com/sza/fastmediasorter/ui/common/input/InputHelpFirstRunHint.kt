package com.sza.fastmediasorter.ui.common.input

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R

/**
 * One-shot first-run hint telling non-touch users (TV / D-pad / hardware keyboard) that F1 opens
 * the keyboard-shortcut help. Shown at most once per installation and only when a non-touch input
 * device is detected; touch-only phones never see it. S0510.
 */
object InputHelpFirstRunHint {

    private const val PREF_KEY = "f1_hint_shown"

    /** Pure decision, unit-tested without a device. */
    fun shouldShow(isNonTouch: Boolean, alreadyShown: Boolean): Boolean = isNonTouch && !alreadyShown

    fun showIfNeeded(activity: AppCompatActivity) {
        val prefs = activity.getSharedPreferences(
            "${activity.packageName}_preferences",
            Context.MODE_PRIVATE,
        )
        if (!shouldShow(isNonTouchDevice(activity), prefs.getBoolean(PREF_KEY, false))) return
        prefs.edit().putBoolean(PREF_KEY, true).apply()
        val root: View = activity.findViewById(android.R.id.content)
        Snackbar.make(root, R.string.keybinding_f1_hint, Snackbar.LENGTH_LONG).show()
    }

    private fun isNonTouchDevice(context: Context): Boolean {
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
        val config = context.resources.configuration
        val isTvUiMode = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
        val hasHardwareKeyboard = config.keyboard != Configuration.KEYBOARD_NOKEYS
        val noTouchscreen = !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        return isTvUiMode || hasHardwareKeyboard || noTouchscreen
    }
}
