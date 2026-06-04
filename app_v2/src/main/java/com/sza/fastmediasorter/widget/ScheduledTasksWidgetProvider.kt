package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.Date

/**
 * Widget provider for Scheduled Tasks (S0353).
 * Renders the active-task count and last-run status, exposes Run All / Pause-Resume controls,
 * and hosts the 2x2 upcoming-task list served by [ScheduledTasksWidgetService].
 */
class ScheduledTasksWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ScheduledTasksWidgetEntryPoint {
        fun database(): AppDatabase
        fun settingsRepository(): SettingsRepository
        fun workManagerScheduler(): WorkManagerScheduler
        fun scheduledOperationRepository(): ScheduledOperationRepository
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action != ACTION_RUN_ALL && action != ACTION_TOGGLE_PAUSE) return

        // Keep the broadcast alive while the suspend scheduler call runs (mirrors BootReceiver).
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ScheduledTasksWidgetEntryPoint::class.java
                )
                when (action) {
                    ACTION_RUN_ALL -> {
                        Timber.d("S0353: widget Run All tapped")
                        entryPoint.workManagerScheduler().runAllNow()
                        Timber.i("ScheduledTasksWidgetProvider: Run All action handled")
                    }
                    ACTION_TOGGLE_PAUSE -> {
                        Timber.d("S0353: widget Pause/Resume tapped")
                        val paused = entryPoint.settingsRepository()
                            .getSettings().first().scheduledOperationsPaused
                        if (paused) {
                            entryPoint.workManagerScheduler().resumeAll()
                            Timber.i("ScheduledTasksWidgetProvider: Resume All action handled")
                        } else {
                            entryPoint.workManagerScheduler().pauseAll()
                            Timber.i("ScheduledTasksWidgetProvider: Pause All action handled")
                        }
                    }
                }
                // Refresh every instance so the status/labels reflect the new state.
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, ScheduledTasksWidgetProvider::class.java)
                )
                for (id in ids) {
                    updateAppWidget(context, manager, id)
                }
            } catch (e: Exception) {
                Timber.e(e, "ScheduledTasksWidgetProvider: failed to handle action=$action")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_RUN_ALL = "com.sza.fastmediasorter.action.SCHEDULED_RUN_ALL"
        const val ACTION_TOGGLE_PAUSE = "com.sza.fastmediasorter.action.SCHEDULED_TOGGLE_PAUSE"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Timber.d("S0353: scheduled-tasks widget rendered")
            val views = RemoteViews(context.packageName, R.layout.widget_scheduled_tasks)
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ScheduledTasksWidgetEntryPoint::class.java
            )

            // Read status synchronously (bounded queries; mirrors FavoritesRemoteViewsFactory).
            var activeCount = 0
            var lastRunAt: Long? = null
            var lastRunStatus: String? = null
            var paused = false
            try {
                runBlocking {
                    val ops = entryPoint.scheduledOperationRepository().getAll().first()
                    activeCount = ops.count { it.isEnabled }
                    val latest = ops.filter { it.lastRunAt != null }.maxByOrNull { it.lastRunAt!! }
                    lastRunAt = latest?.lastRunAt
                    lastRunStatus = latest?.lastRunStatus
                    paused = entryPoint.settingsRepository()
                        .getSettings().first().scheduledOperationsPaused
                }
            } catch (e: Exception) {
                Timber.e(e, "ScheduledTasksWidgetProvider: failed to read status")
            }

            views.setTextViewText(
                R.id.widget_scheduled_active_count,
                context.getString(R.string.widget_scheduled_active_count, activeCount)
            )

            val lastAt = lastRunAt
            if (lastAt != null) {
                val formattedTime = DateFormat.getTimeFormat(context).format(Date(lastAt))
                val statusText = if (lastRunStatus == "OK") {
                    context.getString(R.string.widget_scheduled_last_ok, formattedTime)
                } else {
                    context.getString(R.string.widget_scheduled_last_error, formattedTime)
                }
                views.setTextViewText(R.id.widget_scheduled_last_status, statusText)
            } else {
                views.setTextViewText(R.id.widget_scheduled_last_status, "—")
            }

            views.setTextViewText(
                R.id.widget_scheduled_toggle_pause,
                context.getString(
                    if (paused) R.string.widget_scheduled_resume_all
                    else R.string.widget_scheduled_pause_all
                )
            )

            // Control PendingIntents (broadcast back to this provider).
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val runAllIntent = Intent(context, ScheduledTasksWidgetProvider::class.java)
                .setAction(ACTION_RUN_ALL)
            val runAllPending = PendingIntent.getBroadcast(
                context, appWidgetId * 10 + 1, runAllIntent, flags
            )
            views.setOnClickPendingIntent(R.id.widget_scheduled_run_all, runAllPending)

            val togglePauseIntent = Intent(context, ScheduledTasksWidgetProvider::class.java)
                .setAction(ACTION_TOGGLE_PAUSE)
            val togglePausePending = PendingIntent.getBroadcast(
                context, appWidgetId * 10 + 2, togglePauseIntent, flags
            )
            views.setOnClickPendingIntent(R.id.widget_scheduled_toggle_pause, togglePausePending)

            // Open-settings PendingIntent for the status area - deep-links to the scheduled section.
            val settingsIntent = Intent(context, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_OPEN_SCHEDULED, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val settingsPending = PendingIntent.getActivity(
                context, appWidgetId * 10 + 3, settingsIntent, flags
            )
            views.setOnClickPendingIntent(R.id.widget_scheduled_active_count, settingsPending)
            views.setOnClickPendingIntent(R.id.widget_scheduled_last_status, settingsPending)
            views.setOnClickPendingIntent(R.id.widget_scheduled_empty, settingsPending)

            // Upcoming list adapter + empty view.
            val serviceIntent = Intent(context, ScheduledTasksWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            @Suppress("DEPRECATION")
            views.setRemoteAdapter(R.id.widget_scheduled_list, serviceIntent)
            views.setEmptyView(R.id.widget_scheduled_list, R.id.widget_scheduled_empty)

            // Row clicks open the scheduled section too (rows fill in an empty intent).
            val listTemplateIntent = Intent(context, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_OPEN_SCHEDULED, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val listTemplatePending = PendingIntent.getActivity(
                context, appWidgetId * 10 + 4, listTemplateIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setPendingIntentTemplate(R.id.widget_scheduled_list, listTemplatePending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            @Suppress("DEPRECATION")
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_scheduled_list)
        }
    }
}
