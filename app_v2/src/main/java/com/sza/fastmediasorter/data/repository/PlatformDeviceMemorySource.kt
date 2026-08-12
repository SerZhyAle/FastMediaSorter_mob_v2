package com.sza.fastmediasorter.data.repository

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import com.sza.fastmediasorter.domain.model.devicestatus.MemoryStatus
import com.sza.fastmediasorter.domain.model.devicestatus.MetricValue
import com.sza.fastmediasorter.domain.repository.DeviceMemoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S1178: reads memory and uptime from the platform.
 *
 * The read is pushed to the IO dispatcher even though it looks cheap: it crosses into system_server,
 * and the gadget set must not become the reason the desktop stutters.
 */
class PlatformDeviceMemorySource @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceMemoryRepository {

    override suspend fun read(): MemoryStatus = withContext(Dispatchers.IO) {
        // Uptime comes from the clock, not from the service, so it survives a device that will not
        // hand over its memory figures.
        val uptime = MetricValue.Known(SystemClock.elapsedRealtime())
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (activityManager == null) {
            Timber.i("ActivityManager unavailable - reporting RAM as unknown, not as zero")
            MemoryStatus(
                availableRamBytes = MetricValue.Unknown,
                totalRamBytes = MetricValue.Unknown,
                uptimeMillis = uptime
            )
        } else {
            val info = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(info)
            MemoryStatus(
                availableRamBytes = MetricValue.Known(info.availMem),
                totalRamBytes = MetricValue.Known(info.totalMem),
                uptimeMillis = uptime
            )
        }
    }
}
