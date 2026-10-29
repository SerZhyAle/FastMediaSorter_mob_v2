package com.sza.fastmediasorter.domain.model

/**
 * Contract DEVICE-EXCHANGE 1.6 item DS: a device that holds an entry that may produce always has an outgoing one,
 * so broadcasting and publishing never wait for the person to pick it. Every path that changes the entry list
 * passes through [normalize].
 */
object DeviceExchangeOutgoingRule {

    /**
     * Keeps a producing outgoing entry where it is; otherwise moves the flag to the first entry that may produce,
     * in list order. With no such entry the flags stay as they are: a guest outgoing entry still receives casts
     * (19 item AO). Order and every other field are kept, and at most one entry ends up outgoing.
     */
    fun normalize(entries: List<DeviceExchangeAccountEntry>): List<DeviceExchangeAccountEntry> {
        val outgoing = entries.indexOfFirst { it.outgoing }
        val target = when {
            outgoing >= 0 && entries[outgoing].mayProduce -> outgoing
            else -> entries.indexOfFirst { it.mayProduce }.takeIf { it >= 0 } ?: outgoing
        }
        val unchanged = entries.withIndex().all { (i, entry) -> entry.outgoing == (i == target) }
        return if (unchanged) entries else entries.mapIndexed { i, entry -> entry.copy(outgoing = i == target) }
    }
}
