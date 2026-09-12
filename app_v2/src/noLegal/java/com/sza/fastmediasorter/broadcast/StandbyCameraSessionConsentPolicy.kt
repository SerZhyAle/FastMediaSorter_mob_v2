package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.domain.model.WearCameraRefusal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2551, `noLegal`: consent was given ahead of time, and what proves it is a capture already running.
 *
 * The owner's §6.8 decision gives this flavor the standby shape so that goal 1 survives the phone
 * being in a pocket in another room. `research/05` puts the platform's barrier on service *creation*
 * rather than on a command reaching a service that already exists, which is exactly why this policy
 * grants only while the broadcast layer reports itself live: a session armed while the app was visible
 * keeps serving after it is not, and a phone with nothing armed is not made to start anything here.
 *
 * Starting is deliberately absent - no service is launched here and the controller is only read. A
 * policy that started what it is asked to judge would put the platform barrier back in front of it.
 */
@Singleton
class StandbyCameraSessionConsentPolicy @Inject constructor(
    private val broadcastSourceController: BroadcastSourceController
) : CameraSessionConsentPolicy {

    override suspend fun requestConsent(requestId: String): CameraConsentOutcome =
        if (broadcastSourceController.state.value is BroadcastState.Live) {
            CameraConsentOutcome.Granted
        } else {
            // NOT_ASKED rather than DECLINED: standby is off, so nobody was put the question at all -
            // the watch's user is told to arm the phone, not that the phone said no.
            CameraConsentOutcome.Refused(WearCameraRefusal.NOT_ASKED)
        }
}
