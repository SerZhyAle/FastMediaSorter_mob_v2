package com.sza.fastmediasorter.wear.domain.model

import com.sza.fastmediasorter.wear.domain.model.WearNetworkSourcePayload

data class WearSourcesExportPayload(
    val sources: List<WearNetworkSourcePayload>,
    val watchName: String
)
