package com.sza.fastmediasorter.wear.domain.model

/**
 * One live broadcast of another device of the account, as the watch's list shows it (contract DEVICE-EXCHANGE 9.1
 * and item CI). It carries no password and no address: what plays is resolved from the directory record at the
 * moment of the tap, so a broadcast that ended in between answers "not reachable" instead of a stale address.
 */
data class WearAccountBroadcast(
    val entryIdentity: String,
    val broadcastId: String,
    val title: String?,
    val mode: String?,
    val isProtected: Boolean,
    /** False for a mode the watch does not decode, or a protected one with no endpoint it can answer (item CH). */
    val isPlayable: Boolean,
) {

    val isVideo: Boolean
        get() = mode?.uppercase() in VIDEO_MODES

    // DEVICE-EXCHANGE 7.9: a broadcast id is a capability and a log names it by a hash only.
    override fun toString(): String = "WearAccountBroadcast(mode=$mode, protected=$isProtected, playable=$isPlayable)"

    private companion object {
        val VIDEO_MODES = setOf("VIDEO_AUDIO", "VIDEO_ONLY")
    }
}

/** The broadcasts one device of the account makes, with that device's presence (item CI). */
data class WearBroadcasterGroup(
    val deviceName: String?,
    val isOnline: Boolean,
    val broadcasts: List<WearAccountBroadcast>,
)

/** One exchange entry's part of the list; [isLoaded] is false until the entry's first `directory` arrived. */
data class WearAccountBroadcastSection(
    val entryIdentity: String,
    val entryLabel: String,
    val groups: List<WearBroadcasterGroup>,
    val isReachable: Boolean,
    val isLoaded: Boolean,
    val failure: WearExchangeMessage? = null,
)
