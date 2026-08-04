package com.sza.fastmediasorter.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.widget.RandomPhotoFrameWidgetProvider
import timber.log.Timber
import java.util.concurrent.TimeUnit

class RandomPhotoFrameRefreshWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val UNIQUE_WORK_NAME = "random_photo_frame_widget_refresh"
        const val REFRESH_INTERVAL_MINUTES = 30L

        fun buildWorkRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            return PeriodicWorkRequestBuilder<RandomPhotoFrameRefreshWorker>(
                REFRESH_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(applicationContext, RandomPhotoFrameWidgetProvider::class.java)
        )
        if (appWidgetIds.isEmpty()) {
            return Result.success()
        }

        return try {
            appWidgetIds.forEach { appWidgetId ->
                // S1309: updateAppWidget() refreshes a configured widget itself, so the separate
                // refresh() call here ran the whole Room+gzip+thumbnail round-trip twice per tick
                // and threw the first random pick away.
                RandomPhotoFrameWidgetProvider.updateAppWidget(
                    applicationContext,
                    appWidgetManager,
                    appWidgetId
                )
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "RandomPhotoFrameRefreshWorker: failed to refresh widgets")
            Result.retry()
        }
    }
}