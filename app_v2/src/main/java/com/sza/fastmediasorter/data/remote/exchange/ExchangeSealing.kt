package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eRecipients
import com.sza.fastmediasorter.data.security.e2e.E2eSelf
import com.sza.fastmediasorter.domain.model.ExchangeSealState

/** Who a box of one entry goes to, and how each listed device stands (contract items DG and DR). */
class ExchangeSealing(
    /** This device as a sender and reader; null for an entry enrolled before 0.29, which seals nothing. */
    val self: E2eSelf?,
    val recipients: List<E2eRecipient>,
    val states: Map<String, ExchangeSealState>,
    private val trusted: Map<String, String>,
) {
    /** The opening check of item DH: [fromKey] is the key this device trusts for [from]. */
    fun trusts(from: String, fromKey: ByteArray): Boolean = E2eRecipients.trusts(trusted, from, fromKey)
}
