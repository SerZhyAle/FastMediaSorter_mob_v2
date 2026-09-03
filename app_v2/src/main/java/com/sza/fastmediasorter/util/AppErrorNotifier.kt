package com.sza.fastmediasorter.util

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.error.ErrorSeverity
import timber.log.Timber

/**
 * Unified facade for all application error notifications.
 *
 * Replaces direct [android.widget.Toast] calls for error conditions.
 * Uses [Snackbar] on all API levels, anchored to the activity's decor view.
 *
 * Severity routing:
 * - [ErrorSeverity.CRITICAL]: red background, ⚠ prefix, 6 s on screen. Always shown.
 * - [ErrorSeverity.DEBUG_ONLY]: amber background, ● prefix, 4 s on screen.
 *   Completely suppressed in release builds ([BuildConfig.DEBUG] == false).
 */
object AppErrorNotifier {

    private const val DURATION_CRITICAL_MS = 6_000L
    private const val DURATION_DEBUG_MS = 4_000L

    /**
     * Show a styled error notification.
     *
     * @param activity         Host activity. Must not be finishing or destroyed.
     * @param message          User-readable error text.
     * @param severity         [ErrorSeverity.CRITICAL] or [ErrorSeverity.DEBUG_ONLY].
     * @param screenName       Optional label prepended to log output (e.g. "Browse").
     * @param showDetailedErrors Unused here; caller decides whether to show [com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog]
     *                         separately. Passed for symmetry with existing callers.
     */
    fun show(
        activity: Activity,
        message: String,
        severity: ErrorSeverity,
        screenName: String? = null,
        showDetailedErrors: Boolean = false
    ) {
        Timber.d("S2394: AppErrorNotifier.show entry severity=$severity")
        // DEBUG_ONLY suppressed in release builds
        if (severity == ErrorSeverity.DEBUG_ONLY && !BuildConfig.DEBUG) {
            Timber.d("AppErrorNotifier: suppressed DEBUG_ONLY in release build")
            return
        }

        if (activity.isFinishing || activity.isDestroyed) {
            Timber.w("AppErrorNotifier: Activity is finishing/destroyed - skipping notification")
            return
        }

        val durationMs = when (severity) {
            ErrorSeverity.CRITICAL -> DURATION_CRITICAL_MS
            ErrorSeverity.DEBUG_ONLY -> DURATION_DEBUG_MS
        }

        val bgColorRes = when (severity) {
            ErrorSeverity.CRITICAL -> R.color.error_color
            ErrorSeverity.DEBUG_ONLY -> R.color.warning_color
        }

        val textColor = when (severity) {
            ErrorSeverity.CRITICAL -> Color.WHITE
            ErrorSeverity.DEBUG_ONLY -> Color.BLACK
        }

        val prefix = when (severity) {
            ErrorSeverity.CRITICAL -> "⚠ "
            ErrorSeverity.DEBUG_ONLY -> "● "
        }

        val displayMessage = "$prefix$message"

        val rootView = activity.window.decorView.rootView

        val snackbar = Snackbar.make(rootView, displayMessage, Snackbar.LENGTH_INDEFINITE)
        // backgroundTintList is required on Material3/Android 12+ - setBackgroundColor() only sets
        // the SnackbarLayout outer shell and is overridden by the inner SnackbarContentLayout tint.
        val bgColor = ContextCompat.getColor(activity, bgColorRes)
        snackbar.view.backgroundTintList = ColorStateList.valueOf(bgColor)
        snackbar.setTextColor(textColor)

        // Auto-dismiss after target duration
        Handler(Looper.getMainLooper()).postDelayed({ snackbar.dismiss() }, durationMs)

        // S2394: the only trace a shown error surface leaves in logcat. Maestro logs plain Toasts
        // but never a Snackbar, so without this line the pre-release audit cannot see this path at
        // all. Level carries the severity, so the audit classifies red vs amber without parsing text.
        val logLine = "AppErrorNotifier: shown [$severity]${screenName?.let { " ($it)" } ?: ""}: $message"
        when (severity) {
            ErrorSeverity.CRITICAL -> Timber.e(logLine)
            ErrorSeverity.DEBUG_ONLY -> Timber.w(logLine)
        }

        snackbar.show()
    }
}
