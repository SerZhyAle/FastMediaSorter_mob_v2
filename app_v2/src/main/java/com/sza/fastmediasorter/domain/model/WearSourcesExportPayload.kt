package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.domain.model.WearNetworkSourcePayload

data class WearSourcesExportPayload(
    val sources: List<WearNetworkSourcePayload>,
    val watchName: String
)
