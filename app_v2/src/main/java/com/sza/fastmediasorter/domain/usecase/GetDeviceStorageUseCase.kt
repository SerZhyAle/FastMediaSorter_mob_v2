package com.sza.fastmediasorter.domain.usecase

import android.os.Environment
import android.os.StatFs
import com.sza.fastmediasorter.domain.model.DeviceStorageState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class GetDeviceStorageUseCase @Inject constructor() {

    suspend operator fun invoke(): DeviceStorageState = withContext(Dispatchers.IO) {
        try {
            val path = Environment.getExternalStorageDirectory().absolutePath
            val stat = StatFs(path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val availableGb = availableBytes / (1024.0 * 1024.0 * 1024.0)
            DeviceStorageState.Success(availableGb)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to query device storage")
            DeviceStorageState.Error("Unavailable")
        }
    }
}
