package com.sza.fastmediasorter.ui.sos

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.domain.model.sos.SosMode
import com.sza.fastmediasorter.domain.usecase.sos.IsSosProgramEnabledUseCase
import com.sza.fastmediasorter.service.WearDataLayerPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3216: turns the watch's two SOS commands into a running, or stopped, signal on this phone.
 *
 * A collaborator of its own rather than two branches inside the Data Layer listener, matching the cast
 * and camera-session handlers beside it: the listener sits on detekt's function ceiling, and starting a
 * siren is a decision with a precondition rather than a dispatch.
 *
 * That precondition is the owner's switch. `enableSos` off means the phone's distress signal is not a
 * program on this device, and a paired watch may not overrule that - the watch keeps signalling alone,
 * which is the same autonomous answer it gets when the phone is out of range.
 */
@Singleton
class SosCommandFromWatchHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val isSosProgramEnabled: IsSosProgramEnabledUseCase,
) {

    /**
     * Answers whether [path] is one of the two SOS routes, acting on it when it is.
     *
     * The match lives here rather than in the listener's dispatch because that class is AT detekt's
     * 40-function ceiling; the return value exists for the same reason the cast handler has one - a
     * caller that needs to know whether the route was consumed - and this one's caller does not read it.
     */
    suspend fun handle(path: String, payload: ByteArray): Boolean = run {
        handleRoute(path, payload)
    }

    private suspend fun handleRoute(path: String, payload: ByteArray): Boolean = when (path) {
        WearDataLayerPaths.SOS_START_FROM_WATCH -> {
            handleStart(payload)
            true
        }

        WearDataLayerPaths.SOS_STOP_FROM_WATCH -> {
            handleStop()
            true
        }

        else -> false
    }

    private suspend fun handleStart(payload: ByteArray) {
        if (!isSosProgramEnabled()) {
            Timber.i("SOS: a start arrived from the watch but the phone program is switched off")
            return
        }
        val mode = SosMode.fromNameOrDefault(payload.decodeToString().takeIf { it.isNotBlank() })
        Timber.i("SOS: raising the phone signal in mode %s at the watch's request", mode)
        SosService.start(context, mode)
        // The window as well as the service: half the signal is the flashing screen, and the owner
        // needs the stop control in front of him rather than only in the notification shade.
        context.startActivity(
            SosActivity.createIntent(context, mode).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Unconditional, unlike [handleStart]: the switch decides whether the phone may START signalling,
     * never whether it may stop. A signal running because the switch was on a moment ago must still be
     * stoppable from the watch.
     */
    private fun handleStop() {
        Timber.i("SOS: dropping the phone signal at the watch's request")
        SosService.stop(context)
    }
}
