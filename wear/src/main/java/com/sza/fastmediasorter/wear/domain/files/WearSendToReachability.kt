package com.sza.fastmediasorter.wear.domain.files

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiverEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * S2142: decides whether a receiver the phone marked as watch-served really can be served here.
 *
 * The rule is one-directional, as strategic §5.3 requires: it can only lower a receiver to "through
 * the phone", never raise one. Raising is a change to the declaration on the phone, not a guess made
 * here - so a receiver the phone did not mark stays a phone receiver whatever this watch happens to
 * have installed, and by ADR-3 it is still shown, because "the phone is in another room" is not the
 * same as "this does not exist".
 */
class WearSendToReachability @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * @param sendIntent the share intent the dispatch would actually fire, built by [sendIntentFor].
     * It is taken rather than built here so the caller judges and fires one and the same intent: a
     * rule that answered about an intent nobody sends would drift from the dispatch silently.
     * @return whether this watch itself can serve [entry]. `false` means the receiver is served by
     * the phone, not that it is hidden.
     */
    fun isServedHere(entry: WearSendToReceiverEntry, sendIntent: Intent): Boolean {
        if (!entry.servedOnWatch) return false
        return hasRealHandler(sendIntent)
    }

    /**
     * "Is there an activity accepting this intent" is the wrong question on Wear OS, and answering it
     * would break §11 criterion 10. The measurement in `research/04` (2026-09-03) found that a stock
     * watch answers ACTION_SEND with a system stub whose whole purpose is to say "not available on
     * the watch": counting it would offer the owner an email receiver that does not exist and ends in
     * a refusal - exactly the offer ADR-3 forbids.
     */
    @Suppress("DEPRECATION")
    private fun hasRealHandler(sendIntent: Intent): Boolean =
        context.packageManager.queryIntentActivities(sendIntent, 0)
            .any { it.activityInfo?.packageName != STUB_PACKAGE }

    companion object {
        /** Wear OS system package whose activities exist only to report "not available here". */
        private const val STUB_PACKAGE = "com.google.android.wearable.frameworkpackagestubs"

        /** The share intent both this rule and the dispatch use for a file of [mimeType]. */
        fun sendIntentFor(mimeType: String): Intent =
            Intent(Intent.ACTION_SEND).setType(mimeType)
    }
}
