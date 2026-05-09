package com.sza.fastmediasorter.util

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.error.ErrorSeverity
import timber.log.Timber

/**
 * Prevents toast spam by throttling duplicate/similar messages.
 * Only one network-error toast is shown per [cooldownMs] window.
 *
 * Non-fatal network errors are **debug-only**: always logged via Timber,
 * but the visual notification is only shown in debug builds.
 *
 * When an [Activity] is provided to [showNetworkError], the notification is
 * delegated to [AppErrorNotifier] with [ErrorSeverity.DEBUG_ONLY], which uses
 * a styled Snackbar. When only a [Context] is available (no Activity), the
 * existing throttled plain Toast fallback is used.
 */
object ToastThrottler {

    private const val DEFAULT_COOLDOWN_MS = 15_000L

    private var lastShownTimestamp = 0L
    private var lastShownMessage: String? = null

    /**
     * Shows a throttled notification for non-fatal network errors.
     *
     * - **Debug builds + activity available**: delegates to [AppErrorNotifier] with
     *   [ErrorSeverity.DEBUG_ONLY] (styled Snackbar, throttling handled externally).
     * - **Debug builds + no activity**: falls back to throttled plain Toast.
     * - **Release builds**: suppresses all visual output; message is only logged.
     * - **Both builds**: message is always written to Timber at INFO level.
     *
     * @param activity Optional Activity for the [AppErrorNotifier] Snackbar path.
     */
    @JvmStatic
    fun showNetworkError(
        context: Context,
        message: String,
        duration: Int = Toast.LENGTH_LONG,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS,
        activity: Activity? = null
    ): Boolean {
        Timber.i("Network error (toast): %s", message)

        if (!BuildConfig.DEBUG) return false

        if (activity != null) {
            AppErrorNotifier.show(
                activity = activity,
                message = message,
                severity = ErrorSeverity.DEBUG_ONLY
            )
            return true
        }

        return showThrottled(context, message, duration, cooldownMs)
    }

    /**
     * Shows a toast only if no similar toast was shown within the cooldown window.
     * Shown in all build types. Use [showNetworkError] for non-fatal network errors.
     */
    @JvmStatic
    fun showThrottled(
        context: Context,
        message: String,
        duration: Int = Toast.LENGTH_LONG,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS
    ): Boolean {
        val now = System.currentTimeMillis()
        val elapsed = now - lastShownTimestamp

        if (elapsed < cooldownMs && lastShownMessage == message) {
            Timber.d("ToastThrottler: Suppressed duplicate toast (${elapsed}ms ago): %s", message)
            return false
        }

        lastShownTimestamp = now
        lastShownMessage = message
        Toast.makeText(context, message, duration).show()
        return true
    }

    /** Reset state (e.g. on activity destroy or resource change). */
    @JvmStatic
    fun reset() {
        lastShownTimestamp = 0L
        lastShownMessage = null
    }
}
