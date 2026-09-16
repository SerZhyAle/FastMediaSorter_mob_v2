package com.sza.fastmediasorter.service

import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.CameraConsentOutcome
import com.sza.fastmediasorter.broadcast.CameraSessionConsentGate
import com.sza.fastmediasorter.broadcast.CameraSessionConsentPolicy
import com.sza.fastmediasorter.broadcast.StartWatchCameraBroadcastUseCase
import com.sza.fastmediasorter.broadcast.WatchCameraBroadcast
import com.sza.fastmediasorter.broadcast.WatchCameraSession
import com.sza.fastmediasorter.broadcast.WatchCameraSessionRegistry
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
 *
 * S3117: the decision half moved out to [StartWatchCameraBroadcastUseCase] in `src/main`, because
 * this source set is mounted into shipping variants by directory and into no unit-test variant at
 * all. What is left here is routing: decode, ask the policy, hand the outcome to the ack sender.
 */
class PhoneCameraSessionCommandManager @Inject constructor(
    private val cameraPayloadCodec: WearCameraSessionPayloadCodec,
    private val consentPolicy: CameraSessionConsentPolicy,
    private val consentGate: CameraSessionConsentGate,
    private val ackSender: CameraSessionAckSender,
    private val startWatchCameraBroadcast: StartWatchCameraBroadcastUseCase,
    private val sessions: WatchCameraSessionRegistry,
    private val broadcastController: BroadcastSourceController,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    fun handleStart(nodeId: String, data: ByteArray) {
        val command = cameraPayloadCodec.decodeCommand(data) ?: return
        Timber.d("S3117: camera session start asked by the watch")
        applicationScope.launch { answerStart(nodeId, command.requestId) }
    }

    /**
     * Ends the broadcast only when this watch is what started it.
     *
     * A session the owner armed himself - the `noLegal` standby shape, or a broadcast running for its
     * own listeners - keeps serving: the watch asked to stop watching, which is not the same as
     * asking the phone to stop broadcasting. The watch is answered either way, because `STOPPED` is
     * the answer to a stop rather than a failure.
     */
    fun handleStop(nodeId: String, data: ByteArray) {
        val command = cameraPayloadCodec.decodeCommand(data) ?: return
        Timber.d("S3117: camera session stop asked by the watch")
        val startedByWatch = sessions.current()?.startedByWatch == true
        sessions.clear()
        applicationScope.launch {
            if (startedByWatch) {
                broadcastController.stop()
            }
            ackSender.answerRefusal(nodeId, command.requestId, WearCameraRefusal.STOPPED)
        }
    }

    /**
     * Moves the live session to another lens and re-states the same address.
     *
     * A switch is a new ack for the session already running, not a new session: the URL is the phone's
     * RTSP server, which does not move when the camera behind it does, and re-serving a fresh address
     * would make the watch tear down a player that has nothing wrong with it.
     */
    fun handleSwitch(nodeId: String, data: ByteArray) {
        val command = cameraPayloadCodec.decodeCommand(data) ?: return
        Timber.d("S3117: camera lens switch asked by the watch")
        val lensId = command.lensId
        val session = sessions.current()
        applicationScope.launch {
            if (lensId.isNullOrBlank() || session == null) {
                // Nothing to switch, or nothing to switch it on - a command naming neither is refused
                // rather than answered with an address this phone is not serving.
                ackSender.answerRefusal(nodeId, command.requestId, WearCameraRefusal.NOT_SUPPORTED)
            } else {
                serveLens(nodeId, command.requestId, session, lensId)
            }
        }
    }

    private suspend fun serveLens(
        nodeId: String,
        requestId: String,
        session: WatchCameraSession,
        lensId: String
    ) {
        broadcastController.selectLens(lensId)
        val lenses = session.lenses.copy(activeLensId = lensId)
        sessions.remember(session.copy(requestId = requestId, lenses = lenses))
        ackSender.answerServing(nodeId, requestId, session.url, lenses)
    }

    private suspend fun answerStart(nodeId: String, requestId: String) {
        when (val outcome = consentPolicy.requestConsent(requestId)) {
            is CameraConsentOutcome.Refused -> ackSender.answerRefusal(nodeId, requestId, outcome.refusal)
            CameraConsentOutcome.Granted -> serveSession(nodeId, requestId)
            CameraConsentOutcome.Asked -> awaitOwnerConsent(nodeId, requestId)
        }
    }

    private suspend fun awaitOwnerConsent(nodeId: String, requestId: String) {
        if (consentGate.awaitGrant(requestId, CONSENT_TIMEOUT_MILLIS)) {
            serveSession(nodeId, requestId)
        } else {
            ackSender.answerRefusal(nodeId, requestId, WearCameraRefusal.EXPIRED)
        }
    }

    private suspend fun serveSession(nodeId: String, requestId: String) {
        when (val outcome = startWatchCameraBroadcast()) {
            is WatchCameraBroadcast.Refused ->
                ackSender.answerRefusal(nodeId, requestId, outcome.refusal)

            is WatchCameraBroadcast.Serving -> {
                sessions.remember(
                    WatchCameraSession(
                        nodeId = nodeId,
                        requestId = requestId,
                        url = outcome.url,
                        lenses = outcome.lenses,
                        startedByWatch = outcome.startedNow
                    )
                )
                ackSender.answerServing(nodeId, requestId, outcome.url, outcome.lenses)
            }
        }
    }

    private companion object {
        /** The watch needs a named expiry when a notification prompt goes unanswered. */
        private const val CONSENT_TIMEOUT_MILLIS = 2L * 60L * 1000L
    }
}
