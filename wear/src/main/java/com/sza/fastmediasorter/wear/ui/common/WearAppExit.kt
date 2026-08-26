package com.sza.fastmediasorter.wear.ui.common

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import timber.log.Timber

/**
 * S1975: the home screen's close command, resolved to the host that can actually perform it.
 *
 * finishAndRemoveTask, not finish or finishAffinity: both of those leave the task entry behind, so
 * the next launch returns the screen the user left instead of the home screen - the defect this
 * ticket removes (strategic ADR-1). The phone's exit additionally kills the process, which cures a
 * foreground-service restart this module cannot suffer: it starts no service of its own, and the one
 * declared listener belongs to the system, which raises it again on the next Data Layer event.
 */
@Composable
internal fun rememberCloseAppAction(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            Timber.d("S1975: close command tapped on home, removing the task")
            val activity = context as? Activity
            if (activity == null) {
                // Having no host to finish is a real state, not a swallowed failure - name it.
                Timber.w("Close app: composition host is not an Activity, nothing to finish")
            } else {
                activity.finishAndRemoveTask()
            }
        }
    }
}
