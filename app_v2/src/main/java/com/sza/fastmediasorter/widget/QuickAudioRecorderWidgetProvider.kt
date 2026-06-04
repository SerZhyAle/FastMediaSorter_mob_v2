package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sza.fastmediasorter.R

/**
 * S0349 - one-tap "Quick Audio Recorder" home-screen widget (1x1, icon-only).
 *
 * A tap opens the transparent [QuickAudioRecorderActivity] trampoline, which toggles recording
 * (start on the first tap, stop + save on the next) via [QuickAudioRecorderService]. The service
 * pushes [updateAllWidgets] on each state change so the icon reflects idle vs recording without any
 * periodic refresh (`updatePeriodMillis = 0`).
 *
 * Flavor availability is decided by the merged manifest (receiver removed in `lite`/`photos`); no
 * `BuildConfig.SUPPORT_*` is read here (Rule 15).
 */
class QuickAudioRecorderWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val recording = QuickAudioRecorderService.isRecording
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, recording)
        }
    }

    companion object {

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            isRecording: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_audio_recorder)
            views.setImageViewResource(
                R.id.widget_quick_audio_recorder_icon,
                if (isRecording) {
                    R.drawable.ic_widget_quick_audio_recorder_recording
                } else {
                    R.drawable.ic_widget_quick_audio_recorder_idle
                }
            )

            val intent = Intent(context, QuickAudioRecorderActivity::class.java).apply {
                action = QuickAudioRecorderActivity.ACTION_TOGGLE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_quick_audio_recorder_container, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /** Refresh every placed instance to reflect the current recording state. Called by the service. */
        fun updateAllWidgets(context: Context, isRecording: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, QuickAudioRecorderWidgetProvider::class.java)
            )
            for (id in ids) {
                updateAppWidget(context, manager, id, isRecording)
            }
        }
    }
}
