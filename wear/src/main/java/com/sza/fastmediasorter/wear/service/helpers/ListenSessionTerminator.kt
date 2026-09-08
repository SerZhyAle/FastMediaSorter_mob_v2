package com.sza.fastmediasorter.wear.service.helpers

import android.content.Context
import com.sza.fastmediasorter.wear.domain.listen.ListenSessionState
import com.sza.fastmediasorter.wear.domain.listen.ListenSessionStateHolder
import com.sza.fastmediasorter.wear.service.VoiceRecordingService
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2550: ends a listening session as one action, so acceptance criterion 2 holds.
 *
 * The criterion is that stopping from the phone extinguishes the active-microphone indicator on the
 * watch, and that is only true when the LAN server, the microphone and the request notification end
 * together rather than as three things that can partially fail. The order is fixed: server first so no
 * client can reattach, microphone next, notification last.
 *
 * The first two are the capture service's own `ACTION_STOP`, which performs them in exactly that order
 * and is the only code allowed to touch either - reaching around it would give the recorder a second
 * owner. This class exists rather than three calls at the caller so the Data Layer listener never
 * names the capture service at all: ADR-6 makes the notification tap the sole path that may start the
 * microphone, and a listener that already holds a handle to the service is one edit away from
 * starting it.
 */
@Singleton
class ListenSessionTerminator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateHolder: ListenSessionStateHolder,
    private val notifier: ListenRequestNotifier
) {

    /**
     * A stop with nothing running is a no-op that still clears the request - which is what makes a
     * stop arriving while the request is merely pending cancel the notification and start nothing.
     */
    fun end() {
        val state = stateHolder.state.value
        if (state.isActive) {
            Timber.i("Ending the listening session on request")
            context.startService(VoiceRecordingService.stopIntent(context))
        } else if (state == ListenSessionState.Failed) {
            // The sticky failure is cleared rather than stopped: nothing is running to stop, and
            // `startService` on a service that already ended itself is an IllegalStateException from
            // the background on API 26+ - thrown on the Data Layer delivery thread, which is a crash.
            stateHolder.publish(ListenSessionState.Idle)
        }
        notifier.cancel()
    }
}
