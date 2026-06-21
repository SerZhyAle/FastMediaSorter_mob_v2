package com.sza.fastmediasorter.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Service providing data for the Scheduled Tasks widget upcoming list (2x2, S0353).
 */
class ScheduledTasksWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ScheduledTasksRemoteViewsFactory(applicationContext)
    }
}

class ScheduledTasksRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private data class UpcomingItem(
        val typeName: String,
        val nextRunAt: Long?
    )

    private var items = listOf<UpcomingItem>()

    // Date + time so the upcoming run is unambiguous when it is not today (matches the editor preview).
    private val nextRunFormat = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface ScheduledTasksWidgetEntryPoint {
        fun scheduledOperationRepository(): ScheduledOperationRepository
    }

    override fun onCreate() {
        loadUpcoming()
    }

    override fun onDataSetChanged() {
        loadUpcoming()
    }

    private fun loadUpcoming() {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                ScheduledTasksWidgetEntryPoint::class.java
            )
            items = runBlocking {
                entryPoint.scheduledOperationRepository()
                    .getUpcomingEnabled()
                    .take(3)
                    .map { op ->
                        UpcomingItem(
                            typeName = op.operationType.name,
                            nextRunAt = op.nextRunAt
                        )
                    }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load upcoming scheduled tasks for widget")
            items = emptyList()
        }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_scheduled_tasks_item)
        if (position >= items.size) {
            return views
        }
        val item = items[position]
        views.setTextViewText(R.id.widget_scheduled_item_type, item.typeName)
        val formattedNext = item.nextRunAt
            ?.let { nextRunFormat.format(Date(it)) }
            ?: "-"
        views.setTextViewText(
            R.id.widget_scheduled_item_next,
            context.getString(R.string.widget_scheduled_next_run, formattedNext)
        )
        // Row click routes through the provider's list template (opens the scheduled settings section).
        views.setOnClickFillInIntent(R.id.widget_scheduled_item_root, Intent())
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
