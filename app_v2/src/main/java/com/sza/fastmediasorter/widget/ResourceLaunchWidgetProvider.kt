package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.browse.BrowseActivity

/**
 * Widget provider for quick resource launch
 * Allows user to configure which resource to open
 */
class ResourceLaunchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // Clean up configuration
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (appWidgetId in appWidgetIds) {
            editor.remove("resource_id_$appWidgetId")
            editor.remove("resource_name_$appWidgetId")
        }
        editor.apply()
    }

    override fun onEnabled(context: Context) {
        // First widget added
    }

    override fun onDisabled(context: Context) {
        // Last widget removed
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val resourceId = prefs.getLong("resource_id_$appWidgetId", -1L)
            val resourceName = prefs.getString("resource_name_$appWidgetId", null)

            val views = RemoteViews(context.packageName, R.layout.widget_resource_launch)

            if (resourceId != -1L && resourceName != null) {
                // Widget configured - show resource name and enable click
                views.setTextViewText(R.id.widget_resource_name, resourceName)
                
                // Use BrowseActivity.createIntent for consistency
                val intent = BrowseActivity.createIntent(context, resourceId).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                views.setOnClickPendingIntent(R.id.widget_resource_container, pendingIntent)
            } else {
                // Widget not configured - show setup message
                views.setTextViewText(
                    R.id.widget_resource_name,
                    context.getString(R.string.widget_resource_not_configured)
                )
                
                // Click to open configuration
                val configIntent = Intent(context, ResourceLaunchWidgetConfigActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    configIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                views.setOnClickPendingIntent(R.id.widget_resource_container, pendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
