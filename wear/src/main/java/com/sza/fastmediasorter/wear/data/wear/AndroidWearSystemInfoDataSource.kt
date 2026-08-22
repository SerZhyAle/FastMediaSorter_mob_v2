package com.sza.fastmediasorter.wear.data.wear

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.wear.BuildConfig
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

/**
 * Reads the watch's own facts off the platform.
 *
 * Every read is wrapped and degrades to null. The system-information screen is opened precisely when
 * something already looks wrong, so one fact the device refuses to answer must not cost the user the
 * whole report.
 */
class AndroidWearSystemInfoDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : WearSystemInfoDataSource {

    override val manufacturer: String? get() = read("manufacturer") { Build.MANUFACTURER }

    override val model: String? get() = read("model") { Build.MODEL }

    override val osVersion: String? get() = read("os version") { Build.VERSION.RELEASE }

    override val apiLevel: Int? get() = read("api level") { Build.VERSION.SDK_INT }

    override val appVersion: String? get() = read("app version") { BuildConfig.VERSION_NAME }

    override val buildNumber: String? get() = read("build number") { BuildConfig.VERSION_CODE.toString() }

    override val totalMemoryBytes: Long? get() = memoryInfo()?.totalMem

    override val availableMemoryBytes: Long? get() = memoryInfo()?.availMem

    override val totalStorageBytes: Long?
        get() = storage()?.let { stat -> stat.blockCountLong * stat.blockSizeLong }

    override val availableStorageBytes: Long?
        get() = storage()?.let { stat -> stat.availableBlocksLong * stat.blockSizeLong }

    override suspend fun isPhoneConnected(): Boolean = runCatching {
        Wearable.getNodeClient(context).connectedNodes.await().isNotEmpty()
    }.onFailure { error ->
        Timber.w(error, "System info: connected node lookup failed")
    }.getOrDefault(false)

    private fun memoryInfo(): ActivityManager.MemoryInfo? = read("memory") {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
    }

    // The app's own data directory rather than the whole volume: it is the one path the app is always
    // allowed to measure, and free space anywhere else is not what limits this app on a watch.
    private fun storage(): StatFs? = read("storage") { StatFs(context.filesDir.absolutePath) }

    private fun <T> read(what: String, block: () -> T): T? = runCatching(block)
        .onFailure { error -> Timber.w(error, "System info: %s unavailable", what) }
        .getOrNull()
}
