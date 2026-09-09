package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.domain.model.WearCameraRefusal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks the owner and says whether the question actually reached them.
 *
 * A seam rather than a direct call into the notifier: the whole of the policy below is a branch on
 * this boolean, and binding it lets that branch be judged without a phone in hand.
 */
interface CameraSessionConsentPrompt {

    /** Puts the request for [requestId] in front of the owner; false when it could not be shown. */
    fun ask(requestId: String): Boolean
}

/**
 * S2551, `standard`: the owner is asked every time, and the request waits for the answer.
 *
 * The owner's §6.8 decision gives this flavor the confirm-per-session shape, which is the one a store
 * build can ship: nothing in this phone opens a camera for the watch without a tap that happened after
 * the watch asked.
 *
 * It decides and does not act. Whatever the grant unlocks is started by the party holding the session,
 * so this class stays the same size once the serving half exists.
 */
@Singleton
class NotificationCameraSessionConsentPolicy @Inject constructor(
    private val prompt: CameraSessionConsentPrompt
) : CameraSessionConsentPolicy {

    override suspend fun requestConsent(requestId: String): CameraConsentOutcome =
        if (prompt.ask(requestId)) {
            CameraConsentOutcome.Asked
        } else {
            // Not DECLINED: the owner never saw the request, and telling the watch it was refused
            // would send its user looking for a decision nobody made.
            CameraConsentOutcome.Refused(WearCameraRefusal.NOT_ASKED)
        }
}
