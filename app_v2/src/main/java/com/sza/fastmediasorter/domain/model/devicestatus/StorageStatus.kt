package com.sza.fastmediasorter.domain.model.devicestatus

/**
 * S1178: how much room the device has, as the storage gadget states it.
 *
 * [card] is null when no removable volume is mounted, and that is the whole point of it being nullable:
 * a device without a card must show no card row at all rather than a row of zeroes, which would read as
 * "a card that is full".
 */
data class StorageStatus(
    val internalTotalBytes: MetricValue<Long>,
    val internalAvailableBytes: MetricValue<Long>,
    val card: CardSpace?
) {

    /** The mounted removable volume's own pair of figures. */
    data class CardSpace(
        val totalBytes: MetricValue<Long>,
        val availableBytes: MetricValue<Long>
    )
}
