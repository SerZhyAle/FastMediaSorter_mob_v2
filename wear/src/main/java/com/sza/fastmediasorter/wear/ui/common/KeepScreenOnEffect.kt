package com.sza.fastmediasorter.wear.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

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
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
