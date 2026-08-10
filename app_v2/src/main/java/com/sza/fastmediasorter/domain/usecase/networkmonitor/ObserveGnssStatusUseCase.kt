package com.sza.fastmediasorter.domain.usecase.networkmonitor

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.networkmonitor.GnssStatusDataSource
import com.sza.fastmediasorter.domain.model.networkmonitor.GnssSnapshot
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * S1433: the GNSS section's read path.
 *
 * The source already emits domain types, so this use case exists for the dispatcher alone - and that is not a
 * formality: `GnssStatusDataSource` applies no `flowOn`, exactly like every Phase 05 sampler, and both
 * `registerGnssStatusCallback` and `requestLocationUpdates` are binder IPC that would otherwise run on the
 * main thread the moment the section subscribed.
 *
 * It also keeps `ui` from importing `data`, which is what the section would have to do to reach the source.
 */
class ObserveGnssStatusUseCase @Inject constructor(
    private val dataSource: GnssStatusDataSource,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(): Flow<MonitorSection<GnssSnapshot>> = dataSource.observe().flowOn(ioDispatcher)
}
