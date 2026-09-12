package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.di.MediaCapabilitiesEntryPoint
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.widget.registry.HomeWidgetAccent
import dagger.hilt.android.EntryPointAccessors

/**
 * Widget provider for one-tap access to Camera photos in grid view.
 * Finds the "Camera" local resource and launches BrowseActivity in grid mode.
 *
 * Active only on flavors that support images (MediaCapabilities.supportsImages).
 */
class CameraPhotosWidgetProvider : AppWidgetProvider() {

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
            val views = RemoteViews(context.packageName, R.layout.widget_camera_photos)

            // Widget is framework-instantiated; resolve flavor capabilities via Hilt entry point (Rule 14).
            val caps = EntryPointAccessors.fromApplication(
                context.applicationContext,
                MediaCapabilitiesEntryPoint::class.java
            ).mediaCapabilities()
            if (caps.supportsImages) {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_CAMERA_PHOTOS
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_camera_photos_container, pendingIntent)
            }

            // S2889: the identity glyph takes the sub-program's own tone, resolved in this process
            // because a theme attr inside a RemoteViews drawable resolves against the launcher's theme.
            HomeWidgetAccent.applyIconTint(views, R.id.widget_camera_photos_icon, context, "camera_photos")
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
