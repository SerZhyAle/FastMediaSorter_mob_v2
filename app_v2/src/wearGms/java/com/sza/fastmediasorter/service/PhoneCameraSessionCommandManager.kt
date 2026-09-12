package com.sza.fastmediasorter.service

import com.sza.fastmediasorter.broadcast.CameraConsentOutcome
import com.sza.fastmediasorter.broadcast.CameraSessionConsentGate
import com.sza.fastmediasorter.broadcast.CameraSessionConsentPolicy
import com.sza.fastmediasorter.broadcast.ListBroadcastCameraLensesUseCase
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.model.WearCameraRefusal
import com.sza.fastmediasorter.domain.model.WearCameraSessionPayloadCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Owns the phone side of camera-session commands from the paired watch.
 *
 * The listener service only routes Data Layer paths. Keeping consent, enumeration, and replies here
 * makes the session flow independently testable and prevents the long-lived service from becoming a
 * second owner of the camera policy.
 */
class PhoneCameraSessionCommandManager @Inject constructor(
    private val cameraPayloadCodec: WearCameraSessionPayloadCodec,
    private val consentPolicy: CameraSessionConsentPolicy,
    private val consentGate: CameraSessionConsentGate,
    private val ackSender: CameraSessionAckSender,
    private val listCameraLenses: ListBroadcastCameraLensesUseCase,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    fun handleStart(nodeId: String, data: ByteArray) {
        val command = cameraPayloadCodec.decodeCommand(data) ?: return
        applicationScope.launch {
            ackSender.answerRefusal(nodeId, command.requestId, decideStart(command.requestId))
        }
    }

    fun handleStop(nodeId: String, data: ByteArray) = answerCommand(
        nodeId,
        data,
        WearCameraRefusal.STOPPED
    )

    fun handleSwitch(nodeId: String, data: ByteArray) = answerCommand(
        nodeId,
        data,
        WearCameraRefusal.NOT_SUPPORTED
    )

    private suspend fun decideStart(requestId: String): WearCameraRefusal =
        when (val outcome = consentPolicy.requestConsent(requestId)) {
            is CameraConsentOutcome.Refused -> outcome.refusal
            CameraConsentOutcome.Granted -> refusedBeforeServing()
            CameraConsentOutcome.Asked -> awaitOwnerConsent(requestId)
        }

    private suspend fun awaitOwnerConsent(requestId: String): WearCameraRefusal =
        if (consentGate.awaitGrant(requestId, CONSENT_TIMEOUT_MILLIS)) {
            refusedBeforeServing()
        } else {
            WearCameraRefusal.EXPIRED
        }

    private suspend fun refusedBeforeServing(): WearCameraRefusal {
        val lenses = listCameraLenses()
        Timber.i(
            "Camera session lenses: %d offered, active=%s",
            lenses.lenses.size,
            lenses.activeLensId
        )
        return WearCameraRefusal.NOT_SUPPORTED
    }

    private fun answerCommand(nodeId: String, data: ByteArray, refusal: WearCameraRefusal) {
        val command = cameraPayloadCodec.decodeCommand(data) ?: return
        applicationScope.launch {
            ackSender.answerRefusal(nodeId, command.requestId, refusal)
        }
    }

    private companion object {
        /** The watch needs a named expiry when a notification prompt goes unanswered. */
        private const val CONSENT_TIMEOUT_MILLIS = 2L * 60L * 1000L
    }
}
