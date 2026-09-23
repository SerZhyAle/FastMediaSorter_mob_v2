package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.broadcast.BroadcastEntryActivity

/**
 * S2818: home-screen widget for Live Broadcast - one static button that opens the entry
 * confirmation screen. No configuration, no store, and `updatePeriodMillis` is 0, so it never
 * wakes to poll: the tap hands the start decision to the confirmation screen (strategic ADR-1/ADR-2).
 *
 * Registered only in src/broadcastSource/AndroidManifest.xml, alongside the QS tile.
 */
class BroadcastEntryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_broadcast_entry)
            val label = context.getString(R.string.widget_broadcast_label)
            views.setTextViewText(R.id.widget_broadcast_label, label)
            // Without this the tile is a 1x1 button TalkBack reads as empty (S1916 precedent).
            views.setContentDescription(R.id.widget_broadcast_container, label)

            val intent = Intent(context, BroadcastEntryActivity::class.java).apply {
                action = BroadcastEntryActivity.ACTION_OPEN_BROADCAST_ENTRY
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_broadcast_container, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
