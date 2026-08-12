package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.devicestatus.MemoryStatus

/** S1178: one read of the device's memory and uptime, off the main thread. */
interface DeviceMemoryRepository {

    suspend fun read(): MemoryStatus
}
