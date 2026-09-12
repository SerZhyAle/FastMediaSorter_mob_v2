package com.sza.fastmediasorter.wear.ui.common

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.sza.fastmediasorter.wear.domain.playback.HostTeardownReason
import com.sza.fastmediasorter.wear.domain.playback.WearBackgroundPlaybackPolicy
import com.sza.fastmediasorter.wear.service.WearPlaybackService
import timber.log.Timber

/**
 * S1975: the home screen's close command, resolved to the host that can actually perform it.
 *
 * finishAndRemoveTask, not finish or finishAffinity: both of those leave the task entry behind, so
 * the next launch returns the screen the user left instead of the home screen - the defect this
 * ticket removes (strategic ADR-1). The phone's exit additionally kills the process to cure a
 * foreground-service restart; this module stops its one service explicitly instead, before the task
 * goes, rather than relying on task removal to do it. S2166 ADR-4 is why the order matters: exiting
 * is the single gesture that means "finished", so the sound must be gone by the time the task is,
 * and a stop sent after `finishAndRemoveTask` has no sender left. The one declared listener still
 * belongs to the system, which raises it again on the next Data Layer event.
 */
@Composable
internal fun rememberCloseAppAction(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            val activity = context as? Activity
            if (activity == null) {
                // Having no host to finish is a real state, not a swallowed failure - name it.
                Timber.w("Close app: composition host is not an Activity, nothing to finish")
            } else {
                // stopService, not startService(stopIntent): a stop sent by intent to a service that
                // is not running starts it, and the owner exiting a silent app would watch a media
                // notification appear and vanish. This is a no-op when nothing is playing, and when
                // something is it reaches the same teardown through onDestroy.
                if (WearBackgroundPlaybackPolicy.stopsBackgroundSession(HostTeardownReason.ExplicitExit)) {
                    activity.stopService(WearPlaybackService.stopIntent(activity))
                }
                activity.finishAndRemoveTask()
            }
        }
    }
}

/**
 * S2472: the home screen's minimize command, paired with [rememberCloseAppAction].
 *
 * moveTaskToBack, the same call the host's HOME BackHandler already uses for the same meaning: the
 * app leaves the foreground and nothing else changes. The playback service is deliberately not
 * touched - sending the app away while its sound keeps going is the entire point of the chevron,
 * and the close action above stays the one gesture that means "finished" (S2166 ADR-4).
 */
@Composable
internal fun rememberMinimizeAppAction(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            val activity = context as? Activity
            if (activity == null) {
                Timber.w("Minimize app: composition host is not an Activity, nothing to send back")
            } else {
                activity.moveTaskToBack(true)
            }
        }
    }
}
