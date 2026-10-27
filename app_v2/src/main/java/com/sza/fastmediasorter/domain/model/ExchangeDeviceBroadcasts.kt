package com.sza.fastmediasorter.domain.model

/** One live broadcast of another device of the account, as the streams list shows it (DEVICE-EXCHANGE 9.1, 9.5). */
data class ExchangeDeviceBroadcast(
    val broadcastId: String,
    val deviceName: String?,
    val title: String?,
    val mode: String,
    val playable: Boolean,
    val descriptorJson: String,
    /** Why the record's box did not open here (item CI): the row shows it and the broadcast is never played. */
    val sealNotice: ExchangeMessage? = null,
) {
    override fun toString(): String = "ExchangeDeviceBroadcast(mode=$mode, playable=$playable, sealNotice=$sealNotice)"
}

/** What the "Broadcasts of my devices" section shows while it is open. */
data class ExchangeDeviceBroadcasts(
    val items: List<ExchangeDeviceBroadcast>,
    /** Some store has not answered yet. */
    val pending: Boolean,
    /** Some store could not be read, so the list may be incomplete. */
    val incomplete: Boolean,
)
