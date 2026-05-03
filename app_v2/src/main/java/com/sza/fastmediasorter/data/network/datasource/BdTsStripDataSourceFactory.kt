package com.sza.fastmediasorter.data.network.datasource

import androidx.media3.datasource.DataSource

internal class BdTsStripDataSourceFactory(
    private val upstream: DataSource.Factory
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        BdTsStripDataSource(upstream.createDataSource())
}
