package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.service.WearListenState
import com.sza.fastmediasorter.ui.wear.WatchListenLaunchActivity
import timber.log.Timber

/**
 * S2881 - the listen widget: one widget, two tap zones (plain listen, listen with recording) and a
 * caption naming the session's state.
 *
 * The provider reads state and never writes it: both taps go to the transparent trampoline under
 * its toggle action, and the state arrives through [updateAllWidgets], pushed by the session
 * owner's renderer (strategic pillar E). [lastState] is the provider's own copy of the newest
 * pushed value, so a widget pinned mid-session renders the truth at once - a process restart resets
 * it to idle, which is exactly right, because the session machine lives in that process.
 *
 * The receiver is declared in the wearGms manifest overlay, not the main one, so flavors without
 * the watch bridge never merge it and their widget pickers never offer a call that cannot work.
 */
class WatchListenWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, lastState)
        }
    }

    companion object {

        /** The newest state the renderer pushed; the seed for a widget pinned between pushes. */
        @Volatile
        internal var lastState: WearListenState = WearListenState.Idle()

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            state: WearListenState,
        ) {
            val recording = state is WearListenState.Listening && state.recording
            val views = RemoteViews(context.packageName, R.layout.widget_watch_listen)
            views.setImageViewResource(
                R.id.widget_watch_listen_listen_icon,
                R.drawable.ic_watch,
            )
            // The record button carries the active state in its icon swap - red recorder while the
            // session records, plain microphone otherwise - beside the caption that names it.
            views.setImageViewResource(
                R.id.widget_watch_listen_record_icon,
                if (recording) {
                    R.drawable.ic_widget_quick_audio_recorder_recording
                } else {
                    R.drawable.ic_microphone
                },
            )
            val captionRes = when (state) {
                is WearListenState.Idle -> R.string.watch_listen_state_idle
                is WearListenState.Awaiting -> R.string.watch_listen_state_waiting
                is WearListenState.Listening ->
                    if (state.recording) {
                        R.string.watch_listen_state_recording
                    } else {
                        R.string.watch_listen_state_listening
                    }
            }
            views.setTextViewText(R.id.widget_watch_listen_state, context.getString(captionRes))
            views.setOnClickPendingIntent(
                R.id.widget_watch_listen_listen_zone,
                togglePendingIntent(context, appWidgetId, record = false),
            )
            views.setOnClickPendingIntent(
                R.id.widget_watch_listen_record_zone,
                togglePendingIntent(context, appWidgetId, record = true),
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /** Refresh every placed instance to reflect the session state. Called by the renderer. */
        fun updateAllWidgets(context: Context, state: WearListenState) {
            Timber.d("S2881: widget push, state=%s", state::class.java.simpleName)
            lastState = state
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, WatchListenWidgetProvider::class.java)
            )
            for (id in ids) {
                updateAppWidget(context, manager, id, state)
            }
        }

        /**
         * Both zones toggle through the trampoline: a tap on an active session stops it, a tap on an
         * idle one starts it - so the second tap never starts a second session, and the widget never
         * needs write access to the session itself.
         */
        private fun togglePendingIntent(
            context: Context,
            appWidgetId: Int,
            record: Boolean,
        ): PendingIntent {
            val intent = Intent(context, WatchListenLaunchActivity::class.java).apply {
                action = WatchListenLaunchActivity.ACTION_TOGGLE
                putExtra(WatchListenLaunchActivity.EXTRA_RECORD, record)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return PendingIntent.getActivity(
                context,
                if (record) appWidgetId else appWidgetId or PENDING_INTENT_RECORD_BIT,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private const val PENDING_INTENT_RECORD_BIT = 1 shl 20
    }
}
