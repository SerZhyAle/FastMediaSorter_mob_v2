package com.sza.fastmediasorter.wear.ui.browse

import android.content.IntentSender
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationResult
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * S2142: what is waiting on the owner's answer to a system write confirmation, and what to run
 * again once it arrives.
 *
 * Kept out of the browse ViewModel because it is a small state machine with its own rules - which
 * file asked, whether it has already asked once - and the ViewModel is the wrong place for logic
 * that answers only to itself.
 *
 * Held as state rather than raised as an event: the screen can be recreated between the refusal and
 * the answer, and an event delivered once would be lost with it.
 */
class MediaStoreConsentManager {

    private val _request = MutableStateFlow<IntentSender?>(null)
    val request: StateFlow<IntentSender?> = _request.asStateFlow()

    private var pending: PendingConsent? = null

    /** Names of files that have already spent their one confirmation, so a loop cannot form. */
    private var alreadyAsked: Set<String> = emptySet()

    /**
     * Records the first refusal in [results], if any, and publishes its confirmation.
     *
     * One file at a time, because the refusal arrives per row and each carries its own request.
     * A file that has already been confirmed once is not asked again: if the store still refuses
     * after a granted confirmation, asking a second time would put the same dialog up forever.
     */
    fun raiseIfBlocked(
        results: List<WearFileOperationResult>,
        targets: List<WearMediaFile>,
        operation: WearFileOperation
    ) {
        val blocked = results.firstOrNull {
            it.outcome == WearFileOperationOutcome.NEEDS_CONSENT &&
                it.consentRequest != null &&
                it.fileName !in alreadyAsked
        }
        if (blocked?.consentRequest == null) {
            pending = null
        } else {
            // Only the one file this request was issued for. A move whose transfer already landed
            // asks to come back as a plain delete, so the phone is not sent the same bytes twice.
            pending = PendingConsent(
                operation = blocked.retryAs ?: operation,
                files = targets.filter { it.name == blocked.fileName }
            )
            _request.value = blocked.consentRequest
        }
    }

    /**
     * Clears the waiting confirmation and answers what to run again, or null when nothing should be.
     *
     * A refusal returns null, which leaves the NEEDS_CONSENT line standing - and that line already
     * reads as "not confirmed, nothing changed", which is what actually happened.
     */
    fun consume(granted: Boolean): PendingConsent? {
        val answered = pending
        pending = null
        _request.value = null
        return if (granted && answered != null && answered.files.isNotEmpty()) {
            alreadyAsked = alreadyAsked + answered.files.map { it.name }
            answered
        } else {
            null
        }
    }

    /** Forgets everything, so a fresh run may ask again for a file an earlier run gave up on. */
    fun reset() {
        pending = null
        alreadyAsked = emptySet()
        _request.value = null
    }
}

/**
 * What to run again once the owner has answered the system's write confirmation.
 *
 * The operation is carried alongside the files because the retry is the same call, not a resumed
 * one - the store refused before changing anything, so there is no half-applied state to continue.
 */
data class PendingConsent(
    val operation: WearFileOperation,
    val files: List<WearMediaFile>
)
