package com.sza.fastmediasorter.util

import android.content.Context
import android.widget.Toast
import com.sza.fastmediasorter.BuildConfig
import timber.log.Timber

/**
 * Prevents toast spam by throttling duplicate/similar messages.
 * Only one network-error toast is shown per [cooldownMs] window.
 *
 * Non-fatal network errors are **debug-only**: always logged via Timber,
 * but the Toast is only shown in debug builds.
 */
object ToastThrottler {

    private const val DEFAULT_COOLDOWN_MS = 15_000L

    private var lastShownTimestamp = 0L
    private var lastShownMessage: String? = null

    /**
     * Shows a throttled toast for non-fatal network errors.
     *
     * - **Debug builds**: toast is shown (throttled to avoid spam).
     * - **Release builds**: toast is suppressed; message is only logged.
     * - **Both builds**: message is always written to Timber at INFO level.
     */
    @JvmStatic
    fun showNetworkError(
        context: Context,
        message: String,
        duration: Int = Toast.LENGTH_LONG,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS
    ): Boolean {
        Timber.i("Network error (toast): %s", message)

        if (!BuildConfig.DEBUG) return false

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
