package com.sza.fastmediasorter.widget.networkmonitor

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.domain.repository.NetworkMonitorRepository
import com.sza.fastmediasorter.util.applicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * S1440: keeps the placed Network Monitor widgets current without a timer on the widget itself.
 *
 * Three refresh classes, one per strategic 4.3 rule. Event-driven indicators ride the snapshot the
 * Monitor already observes; tap-driven ones are the provider's business and never appear here; only
 * `LIVE_THROUGHPUT` gets a bounded poll, and only while an instance carrying it is placed.
 *
 * `updatePeriodMillis` stays at zero in `widget_network_monitor_info.xml` and nothing in this file
 * opens a socket - the work it schedules re-renders from flows that are already running.
 */
object NetworkMonitorWidgetRefresher {

    private const val THROUGHPUT_POLL_MINUTES = 15L
    private const val UNIQUE_WORK_NAME = "network_monitor_widget_throughput_refresh"

    @Volatile
    private var snapshotJob: Job? = null

    /** Redraws every placed instance. Forced onto IO because callers reach it from any dispatcher. */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = placedIds(context, manager)
        if (ids.isNotEmpty()) {
            for (id in ids) {
                NetworkMonitorWidgetProvider.updateAppWidget(context, manager, id)
            }
            syncThroughputPoll(context, ids)
        }
    }

    /**
     * Subscribes once to the connectivity snapshot, and only while at least one widget is placed.
     *
     * Idempotent: an already-running subscription is left alone, so the provider may call this from
     * both `onEnabled` and `onUpdate` without stacking collectors.
     */
    fun start(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = placedIds(appContext, manager)
        if (snapshotJob?.isActive != true && ids.isNotEmpty()) {
            val entryPoint = entryPoint(appContext)
            snapshotJob = appContext.applicationScope().launch {
                entryPoint.monitorRepository().observeSnapshot()
                    .distinctUntilChanged()
                    .collect { refresh(appContext) }
            }
            syncThroughputPoll(appContext, ids)
        }
    }

    /** Called when the last instance is removed: nothing is left to draw, so nothing keeps running. */
    fun stop(context: Context) {
        snapshotJob?.cancel()
        snapshotJob = null
        cancelThroughputPoll(context.applicationContext)
    }

    private fun placedIds(context: Context, manager: AppWidgetManager): IntArray =
        manager.getAppWidgetIds(ComponentName(context, NetworkMonitorWidgetProvider::class.java))

    private fun syncThroughputPoll(context: Context, ids: IntArray) {
        val needed = ids.any { id ->
            NetworkMonitorWidgetIndicatorStore.read(context, id)?.refresh == Refresh.VISIBLE_ONLY
        }
        if (needed) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                buildWorkRequest()
            )
        } else {
            cancelThroughputPoll(context)
        }
    }

    private fun cancelThroughputPoll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun buildWorkRequest(): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<NetworkMonitorWidgetThroughputWorker>(
            THROUGHPUT_POLL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()

    private fun entryPoint(context: Context): NetworkMonitorWidgetRefreshEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            NetworkMonitorWidgetRefreshEntryPoint::class.java
        )
}

/**
 * The bounded poll strategic 4.3 allows for live speed alone.
 *
 * It re-renders from the traffic-rate flow the Monitor already owns; it starts no request of its own,
 * and `setRequiresBatteryNotLow` keeps it off a draining device the way the photo frame's worker does.
 */
class NetworkMonitorWidgetThroughputWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    // A worker that lets anything escape is reported as failed and never retried; the refresh path
    // reaches RemoteViews, Room and network flows, so the broad arm is the retry decision itself.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result = try {
        NetworkMonitorWidgetRefresher.refresh(applicationContext)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "NetworkMonitorWidgetThroughputWorker: refresh failed")
        Result.retry()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NetworkMonitorWidgetRefreshEntryPoint {

    fun monitorRepository(): NetworkMonitorRepository
}
