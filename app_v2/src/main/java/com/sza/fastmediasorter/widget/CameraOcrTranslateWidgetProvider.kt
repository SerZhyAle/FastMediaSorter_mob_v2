package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailabilityAccessor
import com.sza.fastmediasorter.ui.main.MainActivity

/**
 * Widget provider for quick Camera OCR Translation flow access.
 * Launches MainActivity with ACTION_CAMERA_OCR_TRANSLATE which forwards to CameraOcrTranslateActivity.
 *
 * Offered only where translation is available - which since S1625 means both compiled into the build
 * and licensed for this device class. `RemoteViews` cannot host an inline explanation, so on an
 * unlicensed device the tile simply carries no action rather than saying why; the explanation belongs
 * to the in-app surfaces, which can show it.
 */
class CameraOcrTranslateWidgetProvider : AppWidgetProvider() {

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
            val views = RemoteViews(context.packageName, R.layout.widget_camera_ocr_translate)

            if (CapabilityAvailabilityAccessor.isTranslationAvailable(context)) {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_CAMERA_OCR_TRANSLATE
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_camera_ocr_translate_container, pendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
