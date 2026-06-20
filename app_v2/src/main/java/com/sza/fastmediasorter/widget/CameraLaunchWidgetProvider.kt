package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.sza.fastmediasorter.R

/**
 * S0568 - config-less 1x1 home-screen widget that launches the unified in-app camera (photo/video).
 *
 * A tap opens the transparent [CameraLaunchActivity] trampoline, which owns the CAMERA runtime
 * permission and the launch/save flow (Rule 3 - no logic here). There is no configuration step: the
 * captured media is always saved to the device public folders by its actual kind, so nothing needs
 * binding per instance.
 *
 * Flavor availability is decided by the merged manifest (receiver present wherever the in-app camera
 * is meaningful); no `BuildConfig.SUPPORT_*` is read here (Rule 15). Runtime degradation (single
 * available mode, or no camera) is handled by the trampoline's manager.
 */
class CameraLaunchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_camera_launch)
            views.setImageViewResource(
                R.id.widget_camera_launch_icon,
                R.drawable.ic_widget_camera_launch_accent
            )

            val intent = Intent(context, CameraLaunchActivity::class.java).apply {
                action = CameraLaunchActivity.ACTION_LAUNCH
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                // Unique data so distinct instances do not share one cached PendingIntent.
                data = Uri.parse("fms://cam-launch/$appWidgetId")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_camera_launch_container, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
