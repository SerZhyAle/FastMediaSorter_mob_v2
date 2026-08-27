package com.sza.fastmediasorter.wear.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

/**
 * Holds the watch screen on while [enabled], and releases it on the way out.
 *
 * The flag lives on the window, so two screens setting it independently would clear each other's on
 * navigation - every caller goes through this one effect rather than touching the window itself.
 */
@Composable
fun KeepScreenOnEffect(enabled: Boolean) {
    val window = LocalContext.current.findActivity()?.window
    DisposableEffect(window, enabled) {
        if (enabled) {
            timber.log.Timber.d("S2095: KeepScreenOnEffect acquire window=%s", window)
            window?.let(KeepScreenOnClaims::acquire)
        }
        onDispose {
            if (enabled) window?.let(KeepScreenOnClaims::release)
        }
    }
}

/**
 * Counts the live claims per window, so the flag is set on the first and cleared only on the last.
 *
 * Weak keys: a claim outliving its Activity would otherwise pin the destroyed window here forever, and a
 * recreated Activity brings a new window that starts its own count from zero.
 */
private object KeepScreenOnClaims {
    private val counts = WeakHashMap<Window, Int>()

    fun acquire(window: Window) {
        val count = counts[window] ?: 0
        if (count == 0) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        counts[window] = count + 1
    }

    fun release(window: Window) {
        val count = counts[window] ?: return
        if (count == 1) {
            counts.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            counts[window] = count - 1
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
