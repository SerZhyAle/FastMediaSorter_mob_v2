package com.sza.fastmediasorter.domain.usecase.devicestatus

import com.sza.fastmediasorter.domain.model.devicestatus.DeviceStatusProvider
import com.sza.fastmediasorter.domain.model.devicestatus.MemoryStatus
import com.sza.fastmediasorter.domain.repository.DeviceMemoryRepository
import javax.inject.Inject

/**
 * S1178: the memory gadget's metric. Carries the period and nothing else - the platform read itself
 * belongs to the repository, so a test can drive this without a device.
 */
class GetMemoryStatusUseCase @Inject constructor(
    private val repository: DeviceMemoryRepository
) : DeviceStatusProvider<MemoryStatus> {

    override val refreshIntervalMs: Long = REFRESH_INTERVAL_MS

    override suspend fun read(): MemoryStatus = repository.read()

    private companion object {
        /** Free RAM and uptime both move continuously; five seconds looks live without polling churn. */
        const val REFRESH_INTERVAL_MS = 5_000L
    }
}
