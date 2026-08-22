package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * S1916 - the home-screen tile that opens one saved channel.
 *
 * A launch tile and nothing more: `updatePeriodMillis` is 0, so it never wakes to poll. That is also why
 * it never reconciles its channel against the catalog - a tile that cannot wake cannot keep an
 * "unavailable" mark honest, so it keeps showing what the configuration screen saved and lets the tap
 * find out (strategic §6 item 4, owner ruling 2026-08-22).
 *
 * The tap targets [StreamPlayLaunchActivity], the same trampoline the pinned stream shortcut uses, so
 * there is exactly one place that decides "play silently or open the Streams screen" and the tile and
 * the shortcut behave identically on the same home screen (strategic ADR-3).
 */
class StreamLaunchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            Timber.d("S1916: tile deleted id=%d", appWidgetId)
            StreamLaunchWidgetStore.delete(context, appWidgetId)
        }
    }

    companion object {

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_stream_launch)
            val config = StreamLaunchWidgetStore.read(context, appWidgetId)
            Timber.d("S1916: tile bind id=%d configured=%b", appWidgetId, config != null)
            if (config == null) {
                bindUnconfigured(context, views, appWidgetId)
            } else {
                bindConfigured(context, views, appWidgetId, config)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun bindConfigured(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            config: StreamLaunchWidgetStore.Config,
        ) {
            val icon = StreamLaunchWidgetStore.readIcon(context, appWidgetId)
            if (icon == null) {
                views.setImageViewResource(R.id.widget_stream_icon, R.drawable.ic_cast)
            } else {
                views.setImageViewBitmap(R.id.widget_stream_icon, icon)
            }
            views.setTextViewText(R.id.widget_stream_label, config.title)
            // Without this the tile is a 1x1 button TalkBack reads as empty (strategic §3.2).
            views.setContentDescription(R.id.widget_stream_container, config.title)

            val intent = StreamPlayLaunchActivity.createIntent(context, config.url)
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_stream_container, pendingIntent)
        }

        /**
         * Reachable when the configuration screen was cancelled but the host placed the tile anyway.
         * Tapping reopens the picker rather than doing nothing.
         */
        private fun bindUnconfigured(context: Context, views: RemoteViews, appWidgetId: Int) {
            views.setImageViewResource(R.id.widget_stream_icon, R.drawable.ic_cast)
            views.setTextViewText(R.id.widget_stream_label, context.getString(R.string.widget_stream_launch_label))
            views.setContentDescription(
                R.id.widget_stream_container,
                context.getString(R.string.widget_stream_launch_label),
            )

            val configIntent = StreamLaunchWidgetConfigActivity.createIntent(context, appWidgetId)
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_stream_container, pendingIntent)
        }
    }
}
