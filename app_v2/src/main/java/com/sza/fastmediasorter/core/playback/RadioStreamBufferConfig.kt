package com.sza.fastmediasorter.core.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import timber.log.Timber
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Centralized radio-stream buffering and recovery limits (S1118, S1148).
 *
 * Two profiles, selected by the "smart stream buffering" streams setting (default OFF):
 * - OFF: byte-identical factory ExoPlayer behavior - [DefaultLoadControl] with stock values and the
 *   stock load-error policy. Clean playback runs exactly as a vanilla player.
 * - ON (smart): a 5 s start-up cushion (10 s after a stall, owner-approved wait) plus silent
 *   loader-level reconnects on connectivity errors - buffered audio keeps playing while the loader
 *   retries with 2/4/8 s backoff, instead of the stock 3-fails-then-fatal player restart.
 *
 * The flag is mirrored into SharedPreferences ([syncMirror]) because players are built synchronously
 * (service onCreate) where the DataStore settings Flow cannot be awaited.
 */
object RadioStreamBufferConfig {

    // Factory DefaultLoadControl values - kept explicit so the smart profile deltas are visible.
    const val MIN_BUFFER_MS = 50_000
    const val MAX_BUFFER_MS = 50_000
    const val BUFFER_FOR_PLAYBACK_MS = 2_500
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000

    // Smart profile: bigger start-up cushion before the first sample (and after a rebuffer) so a
    // shaky connection has headroom; min/max stay factory - live 1x delivery never fills them anyway.
    const val SMART_BUFFER_FOR_PLAYBACK_MS = 5_000
    const val SMART_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 10_000

    const val DIALOG_TIMEOUT_MS = 15_000L
    const val PASSIVE_STATUS_TIMEOUT_MS = 20_000L

    const val BASE_RETRY_DELAY_MS = 2_000L
    const val MAX_RETRY_DELAY_MS = 8_000L

    private const val MIRROR_PREFS = "stream_playback"
    private const val MIRROR_KEY_SMART = "smart_buffering"
    private const val MAX_BACKOFF_SHIFT = 2

    /** Mirror the DataStore setting for synchronous reads at player-build time. */
    fun syncMirror(context: Context, smart: Boolean) {
        context.applicationContext.getSharedPreferences(MIRROR_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(MIRROR_KEY_SMART, smart)
            .apply()
    }

    /** Current smart-buffering flag; safe to call on any thread, cheap enough for error-time reads. */
    fun isSmartBuffering(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(MIRROR_PREFS, Context.MODE_PRIVATE)
            .getBoolean(MIRROR_KEY_SMART, false)

    fun createLoadControl(context: Context): DefaultLoadControl {
        val smart = isSmartBuffering(context)
        Timber.d("Radio load control smart=%b (start=%dms)", smart, startupBufferMs(smart))
        if (!smart) return DefaultLoadControl.Builder().build()
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                SMART_BUFFER_FOR_PLAYBACK_MS,
                SMART_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    /**
     * Loader-level resilience (smart mode only; stock behavior otherwise). Consulted at error time,
     * so flipping the setting takes effect without a player rebuild. Connectivity errors retry
     * "forever" with capped backoff - the audible result is silence-then-resume instead of a fatal
     * stop - while data/format errors keep the stock give-up threshold to avoid infinite loops.
     */
    fun createLoadErrorHandlingPolicy(context: Context): LoadErrorHandlingPolicy {
        val appContext = context.applicationContext
        return object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                if (!isSmartBuffering(appContext)) return super.getRetryDelayMsFor(loadErrorInfo)
                if (isConnectivityError(loadErrorInfo.exception)) {
                    val shift = (loadErrorInfo.errorCount - 1).coerceIn(0, MAX_BACKOFF_SHIFT)
                    val delay = (BASE_RETRY_DELAY_MS shl shift).coerceAtMost(MAX_RETRY_DELAY_MS)
                    Timber.i("Audio diag: loader retry #%d in %dms (connectivity)", loadErrorInfo.errorCount, delay)
                    return delay
                }
                return if (loadErrorInfo.errorCount > DEFAULT_MIN_LOADABLE_RETRY_COUNT) {
                    C.TIME_UNSET
                } else {
                    super.getRetryDelayMsFor(loadErrorInfo)
                }
            }

            override fun getMinimumLoadableRetryCount(dataType: Int): Int =
                if (isSmartBuffering(appContext)) Int.MAX_VALUE else super.getMinimumLoadableRetryCount(dataType)
        }
    }

    private fun startupBufferMs(smart: Boolean): Int =
        if (smart) SMART_BUFFER_FOR_PLAYBACK_MS else BUFFER_FOR_PLAYBACK_MS

    private fun isConnectivityError(error: Throwable?): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.HttpDataSourceException ||
                cause is SocketTimeoutException ||
                cause is ConnectException ||
                cause is UnknownHostException
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }
}
