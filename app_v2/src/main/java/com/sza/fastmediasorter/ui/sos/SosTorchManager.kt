package com.sza.fastmediasorter.ui.sos

import android.content.Context
import com.sza.fastmediasorter.core.screencapture.gesture.DeviceActionHandler
import com.sza.fastmediasorter.domain.model.sos.SosMorseCadence
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3216: the light half of the distress signal - the rear torch strobed on the Morse SOS cadence, with
 * [isLit] published so the screen can flash on the same beat.
 *
 * The torch goes through [DeviceActionHandler] rather than reaching for `CameraManager` here: that class
 * already owns the flash-unit lookup, the best-effort torch state and the degradation on a phone without
 * a flash, and a second copy of that logic would be a second answer to "is the torch on".
 *
 * [isLit] is the whole reason this is not a private detail of the service. A phone with no flash unit
 * still signals - the white screen is the light there, exactly as on the watch - so the phase has to be
 * observable even when the torch call did nothing.
 */
@Singleton
class SosTorchManager @Inject constructor(
    private val deviceActionHandler: DeviceActionHandler,
) {

    private val litState = MutableStateFlow(false)

    /** Whether the signal is in an engaged span right now; false whenever the strobe is not running. */
    val isLit: StateFlow<Boolean> = litState.asStateFlow()

    private var scope: CoroutineScope? = null

    val isRunning: Boolean get() = scope != null

    /** Idempotent: a second call while the strobe runs is a no-op rather than a second loop. */
    fun start(context: Context) {
        if (scope != null) return
        val running = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("sos-strobe"))
        scope = running
        running.launch { runCadence(context) }
    }

    /** Safe to call when nothing is running, and always leaves the torch dark. */
    fun stop(context: Context) {
        scope?.cancel()
        scope = null
        litState.value = false
        deviceActionHandler.setTorch(context, false)
    }

    /**
     * Walks the cadence for as long as the scope lives.
     *
     * The torch is driven inside the loop rather than from a collector of [isLit]: a collector could be
     * absent - the service runs with no UI attached whenever the screen is off - and the strobe is the
     * part of the signal that must not depend on something watching it.
     */
    private suspend fun runCadence(context: Context) {
        Timber.d("SOS strobe started")
        while (true) {
            for (span in SosMorseCadence.cycle) {
                litState.value = span.engaged
                deviceActionHandler.setTorch(context, span.engaged)
                delay(span.durationMs)
            }
        }
    }
}
