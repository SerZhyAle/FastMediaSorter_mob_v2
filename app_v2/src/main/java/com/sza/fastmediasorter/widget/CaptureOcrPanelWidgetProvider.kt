package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.main.MainActivity

class CaptureOcrPanelWidgetProvider : AppWidgetProvider() {

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
        private const val REQUEST_CAMERA_PHOTOS = 1
        private const val REQUEST_CAMERA_OCR = 2

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_capture_ocr_panel)
            views.setOnClickPendingIntent(
                R.id.widget_capture_camera_photos_action,
                actionPendingIntent(context, appWidgetId, REQUEST_CAMERA_PHOTOS, MainActivity.ACTION_CAMERA_PHOTOS)
            )
            views.setOnClickPendingIntent(
                R.id.widget_capture_camera_ocr_action,
                actionPendingIntent(context, appWidgetId, REQUEST_CAMERA_OCR, MainActivity.ACTION_CAMERA_OCR_TRANSLATE)
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun actionPendingIntent(
            context: Context,
            appWidgetId: Int,
            requestOffset: Int,
            action: String
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                appWidgetId * 10 + requestOffset,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
