package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.domain.model.WearCameraRefusal

/**
 * S2551: what the owner has agreed to, when the paired watch asks to see this phone's camera.
 *
 * The three outcomes are distinct because the watch shows a different thing for each. [Granted] opens
 * the stream, [Refused] names a reason the watch can put in words, and [Asked] is the case with no
 * answer yet - the owner has been prompted and will decide with a tap, so the watch must wait rather
 * than report a failure.
 */
sealed interface CameraConsentOutcome {

    /** The owner has already agreed, and the capture half may proceed now. */
    data object Granted : CameraConsentOutcome

    /**
     * The owner has been prompted and the answer arrives later, over a second ack.
     *
     * Distinct from [Granted] because nothing may start yet, and distinct from [Refused] because
     * nothing has been denied - collapsing it into either one would make the watch either open a
     * stream that does not exist or give up on a request the owner is about to allow.
     */
    data object Asked : CameraConsentOutcome

    /** The request is over, for the stated reason. */
    data class Refused(val refusal: WearCameraRefusal) : CameraConsentOutcome
}

/**
 * Decides whether the watch's camera request may proceed on this phone.
 *
 * The answer differs by flavor - `standard` asks the owner with a notification, `noLegal` reads a
 * standing switch - and that difference is expressed by which implementation is bound rather than by
 * a flag read here, because Rule 14 forbids a generated build-flavor guard in `src/main` and the
 * broadcast layer beside this file already forks `BroadcastSourceController` the same way.
 *
 * Suspending because the `standard` implementation posts a notification and touches the notification
 * manager, neither of which belongs on the caller's thread inside a `WearableListenerService`.
 */
interface CameraSessionConsentPolicy {

    suspend fun requestConsent(requestId: String): CameraConsentOutcome
}
