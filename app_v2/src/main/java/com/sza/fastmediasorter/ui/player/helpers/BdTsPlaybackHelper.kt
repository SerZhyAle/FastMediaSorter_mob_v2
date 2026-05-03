package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.datasource.DataSource
import com.sza.fastmediasorter.data.network.datasource.BdTsStripDataSourceFactory

internal fun DataSource.Factory.wrapForBdTs(path: String): DataSource.Factory {
    val lower = path.lowercase()
    return if (lower.endsWith(".m2ts") || lower.endsWith(".m2t")) {
        BdTsStripDataSourceFactory(this)
    } else {
        this
    }
}
