package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.worker.RandomPhotoFrameRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RandomPhotoFrameWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        scheduleRefreshWork(context)
        // S0870: updateAppWidget runs a runBlocking Room+gzip+Gson round-trip (refresh()) - defer to
        // IO and keep the broadcast alive via goAsync(), mirroring ScheduledTasksWidgetProvider (S0727).
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { id ->
                    updateAppWidget(context, appWidgetManager, id)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        scheduleRefreshWork(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            RandomPhotoFrameSnapshotStore.clear(context, appWidgetId, notifyWidgets = false)
        }
        if (AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, RandomPhotoFrameWidgetProvider::class.java))
                .isEmpty()
        ) {
            cancelRefreshWork(context)
        }
    }

    override fun onDisabled(context: Context) {
        cancelRefreshWork(context)
    }

    companion object {
        private fun scheduleRefreshWork(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                RandomPhotoFrameRefreshWorker.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                RandomPhotoFrameRefreshWorker.buildWorkRequest()
            )
        }

        private fun cancelRefreshWork(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(RandomPhotoFrameRefreshWorker.UNIQUE_WORK_NAME)
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val storedSnapshot = RandomPhotoFrameSnapshotStore.read(context, appWidgetId)
            val snapshot = if (storedSnapshot.isConfigured) {
                RandomPhotoFrameWidgetRefresher.refresh(context, appWidgetId)
            } else {
                storedSnapshot
            }
            val views = RemoteViews(context.packageName, R.layout.widget_random_photo_frame)

            if (!snapshot.isConfigured) {
                views.setImageViewResource(
                    R.id.widget_random_photo_frame_image,
                    R.drawable.ic_image
                )
                views.setViewVisibility(R.id.widget_random_photo_frame_overlay, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_random_photo_frame_title,
                    context.getString(R.string.widget_random_photo_frame_empty_title)
                )
                views.setTextViewText(
                    R.id.widget_random_photo_frame_subtitle,
                    context.getString(R.string.widget_random_photo_frame_empty_subtitle)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_random_photo_frame_container,
                    configPendingIntent(context, appWidgetId)
                )
                views.setContentDescription(
                    R.id.widget_random_photo_frame_image,
                    context.getString(R.string.widget_random_photo_frame_label)
                )
            } else if (snapshot.hasRenderablePhoto && snapshot.selectedThumbnailUri.isNotBlank()) {
                views.setImageViewUri(
                    R.id.widget_random_photo_frame_image,
                    Uri.parse(snapshot.selectedThumbnailUri)
                )
                views.setViewVisibility(R.id.widget_random_photo_frame_overlay, View.GONE)
                views.setContentDescription(
                    R.id.widget_random_photo_frame_image,
                    snapshot.resourceName.ifBlank {
                        context.getString(R.string.widget_random_photo_frame_label)
                    }
                )
                views.setOnClickPendingIntent(
                    R.id.widget_random_photo_frame_container,
                    openSelectedPhotoPendingIntent(context, appWidgetId, snapshot)
                )
            } else {
                views.setImageViewResource(
                    R.id.widget_random_photo_frame_image,
                    R.drawable.ic_image
                )
                views.setViewVisibility(R.id.widget_random_photo_frame_overlay, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_random_photo_frame_title,
                    snapshot.resourceName.ifBlank {
                        context.getString(R.string.widget_random_photo_frame_empty_title)
                    }
                )
                views.setTextViewText(
                    R.id.widget_random_photo_frame_subtitle,
                    snapshot.fallbackMessage.ifBlank {
                        context.getString(R.string.widget_random_photo_frame_cache_empty)
                    }
                )
                views.setOnClickPendingIntent(
                    R.id.widget_random_photo_frame_container,
                    browsePendingIntent(context, appWidgetId, snapshot.resourceId)
                )
                views.setContentDescription(
                    R.id.widget_random_photo_frame_image,
                    snapshot.fallbackMessage.ifBlank {
                        context.getString(R.string.widget_random_photo_frame_label)
                    }
                )
            }

            // S1930: a launcher cell has no AppWidget host, and a negative id matches no widget - pushing
            // these views would either throw or poke a stranger. The cell's gadget view redraws itself.
            if (!LauncherWidgetToken.isLauncherToken(appWidgetId)) {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun configPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, RandomPhotoFrameConfigActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            return PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openSelectedPhotoPendingIntent(
            context: Context,
            appWidgetId: Int,
            snapshot: RandomPhotoFrameSnapshotStore.Snapshot
        ): PendingIntent {
            val intent = PlayerActivity.createPanelIntent(
                context = context,
                resourceId = snapshot.resourceId,
                skipAvailabilityCheck = true,
                initialFilePath = snapshot.selectedFilePath
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun browsePendingIntent(
            context: Context,
            appWidgetId: Int,
            resourceId: Long
        ): PendingIntent {
            val intent = BrowseActivity.createIntent(
                context = context,
                resourceId = resourceId,
                skipAvailabilityCheck = true
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 2,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}