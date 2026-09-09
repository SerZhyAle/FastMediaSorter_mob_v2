package com.sza.fastmediasorter.broadcast

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2551: carries the owner's answer from wherever it is given back to whoever is waiting for it.
 *
 * [CameraConsentOutcome.Asked] splits the consent decision over two surfaces - the request arrives in
 * a background listener, and the answer is a tap on a notification minutes later, given on a screen
 * that knows nothing about the watch that asked. Without a gate the
 * answer would have nowhere to go and the watch would wait out its own timeout, which is the one
 * outcome strategic pillar A forbids.
 *
 * Lives in `src/main` rather than beside the flavor that prompts, because the party awaiting the
 * answer is the Data Layer listener, which is mounted by every flavor that has a watch at all.
 *
 * The flow replays its last answer so a grant given between the prompt and the first suspension is
 * not lost; a replayed answer for an older session is harmless because [awaitGrant] matches on the
 * request id rather than on arrival order.
 */
@Singleton
class CameraSessionConsentGate @Inject constructor() {

    private val grants = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = GRANT_SLACK)

    /** The owner allowed the session identified by [requestId]. */
    suspend fun grant(requestId: String) {
        grants.emit(requestId)
    }

    /**
     * Waits for the owner's grant, and reports false when [timeoutMillis] passed without one.
     *
     * A timeout rather than an indefinite wait: an unanswered notification is the ordinary case (the
     * phone is in a pocket), and the watch must be told the request expired instead of holding a
     * session open against a phone that was never going to serve it.
     */
    suspend fun awaitGrant(requestId: String, timeoutMillis: Long): Boolean =
        withTimeoutOrNull(timeoutMillis) { grants.first { it == requestId } } != null

    private companion object {
        /** Room for a few overlapping prompts so a slow awaiter never suspends the granting surface. */
        const val GRANT_SLACK = 4
    }
}
