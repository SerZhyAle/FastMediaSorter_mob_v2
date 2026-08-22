package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.RemoteViews
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.screencapture.ScreenshotAccessibilityServiceHolder

/**
 * 1x1 homescreen widget for noLegal flavor to disable Accessibility Service in 1 tap
 * before opening banking applications.
 */
class NoLegalAccessibilityWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_ACCESSIBILITY) {
            val service = ScreenshotAccessibilityServiceHolder.instance
            if (service != null) {
                service.disableSelf()
                Toast.makeText(
                    context,
                    context.getString(R.string.accessibility_service_status_disabled),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
            }
        }
    }

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
        const val ACTION_TOGGLE_ACCESSIBILITY = "com.sza.fastmediasorter.action.TOGGLE_ACCESSIBILITY"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_accessibility_toggle)
            val intent = Intent(context, NoLegalAccessibilityWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_ACCESSIBILITY
                data = Uri.parse("fms://a11y-toggle/$appWidgetId")
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_accessibility_container, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
