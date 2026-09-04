package com.sza.fastmediasorter.wear.domain.model

import com.sza.fastmediasorter.wear.domain.model.WearNetworkSourcePayload

data class WearSourcesExportPayload(
    val sources: List<WearNetworkSourcePayload>,
    val watchName: String,
    // S2502: when this watch sent the batch, in its own time base. The phone subtracts it from its own
    // delivery time to measure the clock offset before it compares any edit stamp. Wire name must match
    // the phone's `sentAt`.
    val sentAt: Long? = null,
    val tombstones: List<WearSourceTombstonePayload> = emptyList()
)
