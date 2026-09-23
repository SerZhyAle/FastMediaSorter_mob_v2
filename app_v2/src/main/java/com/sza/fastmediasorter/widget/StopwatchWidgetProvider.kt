package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.format.DateUtils
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.stopwatch.StopwatchActivity
import com.sza.fastmediasorter.widget.helpers.StopwatchWidgetStateStore

/**
 * S1411 phase 08 - a home-screen cell that starts and stops its own measurement without opening the
 * application, and opens the full stopwatch screen when its reading is tapped (strategic section 2.9).
 *
 * The reading is a system `Chronometer` driven from a base value rather than a periodic self-update:
 * strategic section 7 records that a cell redrawn only when something happens to redraw it shows a
 * frozen time while the measurement runs, and the alternative to the system's own ticking is an alarm
 * belonging to the widget.
 */
class StopwatchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> toggleOne(context, intent)
            Intent.ACTION_BOOT_COMPLETED -> redrawAfterReboot(context)
            else -> super.onReceive(context, intent)
        }
    }

    private fun toggleOne(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return
        }
        val toggled = StopwatchWidgetStateStore.toggled(
            StopwatchWidgetStateStore.read(context, appWidgetId),
            SystemClock.elapsedRealtime(),
        )
        StopwatchWidgetStateStore.write(context, appWidgetId, toggled)
        updateAppWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
    }

    /**
     * A reboot restarts the elapsed-realtime clock while the stored mark survives it, so every running
     * cell holds a base from the previous boot and would draw a growing negative reading. Nothing else
     * would come and correct it: `updatePeriodMillis` is 0 by design, because the reading is ticked by
     * the system chronometer rather than by an alarm of this widget's own.
     */
    private fun redrawAfterReboot(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, StopwatchWidgetProvider::class.java)
        for (appWidgetId in appWidgetManager.getAppWidgetIds(component)) {
            val restarted = StopwatchWidgetStateStore.afterClockRestart(
                StopwatchWidgetStateStore.read(context, appWidgetId),
            )
            StopwatchWidgetStateStore.write(context, appWidgetId, restarted)
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    /** Rule 20: a cell's stored measurement is unreachable once the cell is gone, so it goes with it. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            StopwatchWidgetStateStore.delete(context, appWidgetId)
        }
    }

    companion object {

        private const val ACTION_TOGGLE = "com.sza.fastmediasorter.action.STOPWATCH_WIDGET_TOGGLE"

        /** `DateUtils.formatElapsedTime` takes seconds and renders the same shape a `Chronometer` does. */
        private const val MILLIS_PER_SECOND = 1000L

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val state = StopwatchWidgetStateStore.read(context, appWidgetId)
            val toggleLabel = context.getString(
                if (state.running) R.string.stopwatch_action_stop else R.string.stopwatch_action_start
            )
            val views = RemoteViews(context.packageName, R.layout.widget_stopwatch)
            views.setChronometer(
                R.id.widget_stopwatch_reading,
                StopwatchWidgetStateStore.chronometerBase(state, SystemClock.elapsedRealtime()),
                null,
                state.running,
            )
            if (!state.running) {
                // A stopped Chronometer is not frozen. The host re-applies its cached RemoteViews when it
                // restarts, and `setBase` recomputes `elapsedRealtime() - base` every time, so a base that
                // was relative to the moment of drawing reads an hour later as an hour more - and as a
                // negative duration after a reboot. A literal survives every re-apply unchanged.
                views.setTextViewText(
                    R.id.widget_stopwatch_reading,
                    DateUtils.formatElapsedTime(state.accumulatedMillis / MILLIS_PER_SECOND),
                )
            }
            views.setImageViewResource(
                R.id.widget_stopwatch_toggle,
                if (state.running) R.drawable.ic_stop else R.drawable.ic_play,
            )
            views.setContentDescription(R.id.widget_stopwatch_toggle, toggleLabel)
            views.setOnClickPendingIntent(
                R.id.widget_stopwatch_reading,
                openScreenIntent(context, appWidgetId),
            )
            views.setOnClickPendingIntent(
                R.id.widget_stopwatch_toggle,
                toggleIntent(context, appWidgetId),
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * The cell's own id is the request code for both pending intents. A `PendingIntent` ignores
         * extras when deciding identity, so without it every placed cell would share one toggle intent
         * and the last cell drawn would take over the others.
         */
        private fun openScreenIntent(context: Context, appWidgetId: Int): PendingIntent {
            // Added rather than assigned: an assignment would drop whatever `createIntent` sets.
            val intent = StopwatchActivity.createIntent(context)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun toggleIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, StopwatchWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            return PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
