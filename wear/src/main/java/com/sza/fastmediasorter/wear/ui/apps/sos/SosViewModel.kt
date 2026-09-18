package com.sza.fastmediasorter.wear.ui.apps.sos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.di.ApplicationScope
import com.sza.fastmediasorter.wear.domain.model.SosMode
import com.sza.fastmediasorter.wear.domain.model.SosMorseCadence
import com.sza.fastmediasorter.wear.domain.sos.SosSyncBus
import com.sza.fastmediasorter.wear.domain.usecase.SendSosCommandToPhoneUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S3216: holds the watch's distress signal - which mode is running, and which span of the Morse cadence
 * the display is in.
 *
 * Two states rather than one (ADR-3): while [mode] is null the screen is ARMING and its three mode chips
 * are live; the moment a mode is chosen the screen locks and only a hardware key leaves. The lock exists
 * for wet glass during the signal, which is the only time wet-glass input is the hazard - locking the
 * program's own entrance as well would leave the watch unable to pick a mode at all.
 *
 * The cadence is walked here rather than in the composable so the phase survives a recomposition, and so
 * the siren and the screen step together off one clock.
 */
@HiltViewModel
class SosViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val soundGenerator: SosSoundGenerator,
    private val syncBus: SosSyncBus,
    private val sendSosCommandToPhone: SendSosCommandToPhoneUseCase,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val runningMode = MutableStateFlow<SosMode?>(null)
    private val litState = MutableStateFlow(false)

    /** The mode the signal runs in, or null while the screen is still arming. */
    val mode: StateFlow<SosMode?> = runningMode.asStateFlow()

    /** Whether the cadence is in an engaged span, so the display can flash with the siren. */
    val isLit: StateFlow<Boolean> = litState.asStateFlow()

    /** The paired phone asking this watch to stop, which the screen turns into leaving. */
    val stopRequests: SharedFlow<Unit> = syncBus.stopRequests

    /** The mode the phone asked for, for the screen to start in without a tap. */
    val pendingStartMode: StateFlow<SosMode?> = syncBus.pendingStartMode

    private var cadenceJob: Job? = null

    /**
     * Starts, or reshapes, the signal in [mode], and asks the paired phone for the same.
     *
     * [echoToPhone] is false for the one case that would otherwise loop: a start that ARRIVED from the
     * phone, which is already signalling in that mode and does not need to be told so (§06.4).
     */
    fun start(mode: SosMode, echoToPhone: Boolean = true) {
        syncBus.consumeStart()
        runningMode.value = mode
        if (mode.engagesSound) soundGenerator.start(appContext) else soundGenerator.stop(appContext)
        if (mode.engagesLight) startCadence() else stopCadence()
        if (echoToPhone) {
            applicationScope.launch { sendSosCommandToPhone.start(mode) }
        }
        Timber.i("SOS signal running on the watch in mode %s", mode)
    }

    /**
     * Ends the signal and returns the screen to its arming state. Safe when nothing is running.
     *
     * [echoToPhone] is false when the stop ARRIVED from the phone, for [start]'s reason. On the
     * application scope because the screen is being left as this runs, and a command cancelled with
     * the view-model scope would leave the phone sounding alone.
     */
    fun stop(echoToPhone: Boolean = true) {
        val wasRunning = runningMode.value != null
        stopCadence()
        soundGenerator.stop(appContext)
        runningMode.value = null
        if (echoToPhone && wasRunning) {
            applicationScope.launch { sendSosCommandToPhone.stop() }
        }
    }

    /**
     * The siren reaches for a device-wide resource - the alarm volume - so it must not outlive the
     * screen that started it by any route, including the process being torn down around it.
     */
    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private fun startCadence() {
        if (cadenceJob != null) return
        cadenceJob = viewModelScope.launch {
            while (true) {
                for (span in SosMorseCadence.cycle) {
                    litState.value = span.engaged
                    delay(span.durationMs)
                }
            }
        }
    }

    private fun stopCadence() {
        cadenceJob?.cancel()
        cadenceJob = null
        litState.value = false
    }
}
