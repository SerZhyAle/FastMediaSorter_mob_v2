package com.sza.fastmediasorter.wear.domain.model

/**
 * S1944: which player a channel opens in, and the id its route carries.
 *
 * A model rather than a type nested in the use case, because `domain/usecase` holds use cases and
 * nothing else (CLAUDE.md Rule 6, enforced by the class-architecture-naming gate).
 */
data class WearStreamPlaybackTarget(
    val fileId: Long,
    val isVideo: Boolean,
    // S4261: a handed-off broadcast no endpoint of which plays from here; nothing was selected, so a caller
    // shows why instead of navigating.
    val unplayable: Boolean = false,
)

/**
 * S1944: the answer a stream transfer sends back to the phone, and the channel it stored.
 *
 * The channel travels with the ack because the phone can now ask for the channel to be opened, and
 * the caller needs the record the list would have used rather than the raw payload.
 */
data class WearStreamStoreResult(
    val ack: WearStreamTransferAck,
    val channel: WearStreamChannel?,
)
