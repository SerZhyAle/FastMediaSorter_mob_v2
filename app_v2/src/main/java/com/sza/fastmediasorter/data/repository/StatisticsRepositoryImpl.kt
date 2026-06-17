package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.local.preferences.StatsAggregateDataStore
import com.sza.fastmediasorter.data.local.preferences.StatsBaselineDataStore
import com.sza.fastmediasorter.domain.repository.StatisticsRepository
import com.sza.fastmediasorter.domain.stats.StatsSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles the read snapshot from the always-on baseline store and the detailed aggregate
 * store, and forwards the two maintenance operations (S0473).
 */
@Singleton
class StatisticsRepositoryImpl @Inject constructor(
    private val baseline: StatsBaselineDataStore,
    private val aggregate: StatsAggregateDataStore,
    @IoDispatcher private val io: CoroutineDispatcher
) : StatisticsRepository {

    override suspend fun getSnapshot(): StatsSnapshot = withContext(io) {
        val base = baseline.snapshot()
        val agg = aggregate.read()
        StatsSnapshot(
            baseline = base,
            counters = agg.counters,
            byType = agg.byType
        )
    }

    override suspend fun wipeDetailed() = withContext(io) {
        aggregate.wipeDetailed()
    }

    override suspend fun recordLaunch(installVersion: String, installFlavor: String) =
        withContext(io) {
            baseline.recordLaunch(installVersion, installFlavor)
        }
}
