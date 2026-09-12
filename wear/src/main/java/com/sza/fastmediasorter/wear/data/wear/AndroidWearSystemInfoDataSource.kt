package com.sza.fastmediasorter.wear.data.wear

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.wear.BuildConfig
import com.sza.fastmediasorter.wear.domain.repository.WearNodeDescriptor
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * How long any one Data Layer lookup may hold the report.
 *
 * Every wait is bounded because an unresponsive Play Services stopped the whole screen before this:
 * the report is the thing a user opens when the link is misbehaving, so the one case where the wait
 * never returns is exactly the case the report has to survive (S2165 §11 criterion 4).
 */
private const val DATA_LAYER_BUDGET_MILLIS = 3_000L

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

    override val appDataBytes: Long? get() = read("app data size") { storageStats()?.dataBytes }

    override val appCacheBytes: Long? get() = read("app cache size") { storageStats()?.cacheBytes }

    // getCacheQuotaBytes takes the volume alone and answers for the calling app - unlike
    // queryStatsForUid just above it, which is a general query and therefore names the uid.
    override val cacheQuotaBytes: Long?
        get() = read("cache quota") {
            storageManager()?.getCacheQuotaBytes(StorageManager.UUID_DEFAULT)
        }

    override suspend fun connectedNodes(): List<WearNodeDescriptor>? = dataLayer("connected nodes") {
        Wearable.getNodeClient(context).connectedNodes.await().map(::toDescriptor)
    }

    override suspend fun localNode(): WearNodeDescriptor? = dataLayer("local node") {
        toDescriptor(Wearable.getNodeClient(context).localNode.await())
    }

    override suspend fun pairCapabilities(): List<String>? = dataLayer("pair capabilities") {
        Wearable.getCapabilityClient(context)
            .getAllCapabilities(CapabilityClient.FILTER_REACHABLE)
            .await()
            .keys
            .sorted()
    }

    private fun toDescriptor(node: Node) =
        WearNodeDescriptor(id = node.id, displayName = node.displayName, isNearby = node.isNearby)

    /**
     * A lookup that ran out of its budget answers null on the same footing as one that threw: from the
     * report's point of view the watch did not answer, and which of the two happened is a log line
     * rather than a screen line.
     */
    private suspend fun <T> dataLayer(what: String, block: suspend () -> T): T? = runCatching {
        val answer = withTimeoutOrNull(DATA_LAYER_BUDGET_MILLIS) { block() }
        if (answer == null) {
            Timber.w("System info: %s timed out after %d ms", what, DATA_LAYER_BUDGET_MILLIS)
        }
        answer
    }.onFailure { error ->
        Timber.w(error, "System info: %s unavailable", what)
    }.getOrNull()

    private fun memoryInfo(): ActivityManager.MemoryInfo? = read("memory") {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
    }

    /**
     * The app's own footprint, which needs no permission for its own uid - unlike the same query aimed
     * at any other package.
     */
    private fun storageStats() = context
        .getSystemService(Context.STORAGE_STATS_SERVICE)
        .let { service -> service as? StorageStatsManager }
        ?.queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid())

    private fun storageManager(): StorageManager? =
        context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager

    private fun <T> read(what: String, block: () -> T?): T? = runCatching(block)
        .onFailure { error -> Timber.w(error, "System info: %s unavailable", what) }
        .getOrNull()
}
