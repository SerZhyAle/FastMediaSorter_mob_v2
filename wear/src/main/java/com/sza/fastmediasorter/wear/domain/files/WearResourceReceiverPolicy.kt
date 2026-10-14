package com.sza.fastmediasorter.wear.domain.files

import com.sza.fastmediasorter.wear.domain.model.NetworkSource

/**
 * S4230: which of the watch's own network resources may be offered as a "Send to.." receiver.
 *
 * A receiver that would end in a refusal is not shown at all, so each condition below is a reason the write cannot
 * succeed whatever the network does.
 */
object WearResourceReceiverPolicy {

    /**
     * @param pinHeld whether the watch holds the original PIN of this folder's root, which only the phone hands over.
     */
    fun isOffered(source: NetworkSource, pinHeld: Boolean): Boolean =
        !source.readOnly &&
            (!source.pinProtected || pinHeld) &&
            // Exchange-bound folders wait for the account route of S4228 (tunnel, root proof): offering one before
            // that route exists would only work at home. The last phase of S4230 removes this line.
            source.exchangeResourceId == null
}
