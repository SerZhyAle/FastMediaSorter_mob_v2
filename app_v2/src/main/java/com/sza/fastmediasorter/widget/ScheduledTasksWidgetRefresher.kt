package com.sza.fastmediasorter.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.sza.fastmediasorter.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Pushes an immediate refresh to every placed Scheduled Tasks widget instance (S0353).
 * Called after a scheduled run completes and after aggregate Run All / Pause / Resume commands,
 * so the status surface updates on events instead of waiting for the 30-minute update period.
 * No-op when no widget is placed.
 */
object ScheduledTasksWidgetRefresher {

    // S0870: updateAppWidget() runs a runBlocking Room+DataStore round-trip - every caller of this
    // suspend fun (WorkManagerScheduler.pauseAll/runAllNow/resumeAll, ScheduledOperationsWorker) may
    // run on Dispatchers.Main.immediate (e.g. viewModelScope), so force IO here instead of trusting
    // each caller to dispatch correctly.
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ScheduledTasksWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return@withContext
            for (id in ids) {
                ScheduledTasksWidgetProvider.updateAppWidget(context, manager, id)
            }
            @Suppress("DEPRECATION")
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_scheduled_list)
        } catch (e: Exception) {
            Timber.e(e, "ScheduledTasksWidgetRefresher: refresh failed")
        }
    }
}
