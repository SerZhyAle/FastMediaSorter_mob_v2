package com.sza.fastmediasorter.wear.data.exchange

/**
 * Contract DEVICE-EXCHANGE 1.6 item DS on the watch: a watch holding an entry that may produce always has an
 * outgoing one. The rule of the phone, over [WearExchangeEntry.mayProduce]; order and every other field are kept.
 */
internal object WearExchangeOutgoingRule {

    fun normalize(entries: List<WearExchangeEntry>): List<WearExchangeEntry> {
        val outgoing = entries.firstOrNull { it.outgoing }
        val first = entries.firstOrNull { it.mayProduce }
        // With no entry that may produce the flags stay: a guest outgoing entry keeps receiving casts (19 item AO).
        return if (outgoing?.mayProduce == true || first == null) {
            entries
        } else {
            entries.map { it.copy(outgoing = it.identity == first.identity) }
        }
    }
}
