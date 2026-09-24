package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.domain.model.WearCameraRefusal
import timber.log.Timber
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

    override suspend fun requestConsent(requestId: String): CameraConsentOutcome {
        return answer()
    }

    private fun answer(): CameraConsentOutcome =
        // A camera session, not merely a live one: an audio broadcast the owner started for the room
        // says nothing about whether his watch may see through his camera (S2551 step 06.3).
        if (broadcastSourceController.state.value.isCameraLive()) {
            CameraConsentOutcome.Granted
        } else {
            // NOT_ARMED rather than DECLINED or NOT_ASKED: nobody said no, and nothing failed to be
            // asked either - standby is simply off. Only this constant sends the watch's user to the
            // one remedy that works here, which is arming the capture on the phone (S3211).
            CameraConsentOutcome.Refused(WearCameraRefusal.NOT_ARMED)
        }
}
