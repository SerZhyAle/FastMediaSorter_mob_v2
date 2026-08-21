package com.sza.fastmediasorter.widget.networkmonitor

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.networkmonitor.NetworkIndicatorFormatter
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkIndicatorReading
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ObserveNetworkIndicatorUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ResolveNetworkIndicatorOnDemandUseCase
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * S1440: the configurable Network Monitor home-screen widget.
 *
 * One provider serves all eight indicators; which one a placed instance shows is a per-id choice held
 * by [NetworkMonitorWidgetIndicatorStore] and written by the configuration activity.
 *
 * The receiver is declared only in `src/networkMonitor/AndroidManifest.xml`, which gradle injects for
 * standard and noLegal - exactly the flavors that ship the Monitor. That manifest is the flavor gate,
 * so no compile-time capability flag appears anywhere in this package (CLAUDE.md Rule 14).
 *
 * An `AppWidgetProvider` is a `BroadcastReceiver` and has no constructor to inject into, so the three
 * Phase-02 collaborators are reached through [NetworkMonitorWidgetEntryPoint].
 */
class NetworkMonitorWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NetworkMonitorWidgetEntryPoint {
        fun observeIndicator(): ObserveNetworkIndicatorUseCase
        fun resolveOnDemand(): ResolveNetworkIndicatorOnDemandUseCase
        fun formatter(): NetworkIndicatorFormatter
    }

    override fun onEnabled(context: Context) {
        NetworkMonitorWidgetRefresher.start(context)
    }

    override fun onDisabled(context: Context) {
        NetworkMonitorWidgetRefresher.stop(context)
    }

    // An uncaught throw inside goAsync kills the whole process, not just this cell, and the render
    // path spans RemoteViews, Room and network flows - no narrower set covers what they can raise.
    @Suppress("TooGenericExceptionCaught")
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Timber.d("S1440: widget onUpdate, ids=%d", appWidgetIds.size)
        // Rendering collects a cold flow, so it cannot run inline on the receiver's main thread.
        // goAsync keeps the broadcast alive while the IO coroutine draws every instance.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
                // onEnabled fires only for the first instance ever placed, so after a process death
                // nothing would re-subscribe; start() is idempotent and cheap when already running.
                NetworkMonitorWidgetRefresher.start(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "NetworkMonitorWidgetProvider: onUpdate failed")
            } finally {
                pendingResult.finish()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent
            .takeIf { it.action == ACTION_RESOLVE_ON_DEMAND }
            ?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            super.onReceive(context, intent)
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                resolveOnDemand(context.applicationContext, appWidgetId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "NetworkMonitorWidgetProvider: on-demand resolve failed id=$appWidgetId")
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            NetworkMonitorWidgetIndicatorStore.clear(context, appWidgetId)
            onDemandReadings.remove(appWidgetId)
        }
    }

    companion object {

        /** Sent by the widget's own icon; the only thing allowed to start a network round trip. */
        const val ACTION_RESOLVE_ON_DEMAND =
            "com.sza.fastmediasorter.action.NETWORK_MONITOR_WIDGET_RESOLVE"

        private const val REQUEST_SLOTS = 2
        private const val REQUEST_SLOT_OPEN_MONITOR = 0
        private const val REQUEST_SLOT_RESOLVE = 1

        /**
         * A cold indicator flow may never emit at all - a denied permission or an absent radio simply
         * produces nothing - and a broadcast that waits forever is killed with its widget unpainted.
         */
        private const val FIRST_EMISSION_TIMEOUT_MS = 3_000L

        private const val NO_READING = "no-reading"
        private const val UNKNOWN_NETWORK = "unknown"

        /**
         * Last tap-driven answer per widget id, in memory only.
         *
         * Deliberately not persisted: an external address or a reachability verdict is true of the
         * network the tap happened on, and restoring it after a process death would show yesterday's
         * answer as though it were current. Losing it means the cell asks for a tap again, which is
         * the honest state.
         */
        private val onDemandReadings = ConcurrentHashMap<Int, NetworkIndicatorReading>()

        /**
         * Draws one placed instance.
         *
         * Suspending rather than blocking so the configuration activity can call it from its own scope
         * without a `runBlocking` on the main thread.
         */
        suspend fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val indicator = NetworkMonitorWidgetIndicatorStore.read(context, appWidgetId)
            if (indicator == null) {
                appWidgetManager.updateAppWidget(appWidgetId, unconfiguredViews(context))
                return
            }
            val reading = readingFor(context, indicator, appWidgetId)
            render(context, appWidgetManager, appWidgetId, indicator, reading)
        }

        private fun unconfiguredViews(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_network_monitor).apply {
                setTextViewText(
                    R.id.widget_network_monitor_primary,
                    context.getString(R.string.widget_network_monitor_unavailable)
                )
                setTextViewText(
                    R.id.widget_network_monitor_caption,
                    context.getString(R.string.widget_network_monitor_label)
                )
                setImageViewResource(R.id.widget_network_monitor_icon, R.drawable.ic_network_monitor)
            }

        private fun render(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            indicator: NetworkMonitorIndicator,
            reading: NetworkIndicatorReading
        ) {
            val formatted = entryPoint(context).formatter().format(indicator, reading)
            val views = RemoteViews(context.packageName, R.layout.widget_network_monitor)
            views.setImageViewResource(R.id.widget_network_monitor_icon, formatted.iconRes)
            views.setTextViewText(R.id.widget_network_monitor_primary, formatted.primary)
            // Without a caption of its own the cell still has to say which of the eight it is.
            views.setTextViewText(
                R.id.widget_network_monitor_caption,
                formatted.caption ?: context.getString(indicator.labelRes)
            )
            bindIntents(context, views, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun bindIntents(context: Context, views: RemoteViews, appWidgetId: Int) {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val section = NetworkMonitorWidgetIndicatorStore.readSection(context, appWidgetId)
            // The factory owns the section extra; no second route or entry point is introduced here.
            val open = NetworkMonitorActivity.createIntent(context, section)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(
                R.id.widget_network_monitor_container,
                PendingIntent.getActivity(
                    context,
                    requestCode(appWidgetId, REQUEST_SLOT_OPEN_MONITOR),
                    open,
                    flags
                )
            )
            val resolve = Intent(context, NetworkMonitorWidgetProvider::class.java)
                .setAction(ACTION_RESOLVE_ON_DEMAND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            views.setOnClickPendingIntent(
                R.id.widget_network_monitor_icon,
                PendingIntent.getBroadcast(
                    context,
                    requestCode(appWidgetId, REQUEST_SLOT_RESOLVE),
                    resolve,
                    flags
                )
            )
        }

        private fun requestCode(appWidgetId: Int, slot: Int): Int = appWidgetId * REQUEST_SLOTS + slot

        /**
         * Never starts a round trip: a tap-driven indicator shows whatever the last tap produced, and
         * `Awaiting` until one happens (strategic 3.4 forbids automatic external traffic).
         */
        private suspend fun readingFor(
            context: Context,
            indicator: NetworkMonitorIndicator,
            appWidgetId: Int
        ): NetworkIndicatorReading = if (indicator.refresh == Refresh.ON_TAP) {
            onDemandReadings[appWidgetId] ?: NetworkIndicatorReading.Awaiting
        } else {
            withTimeoutOrNull(FIRST_EMISSION_TIMEOUT_MS) {
                entryPoint(context).observeIndicator()
                    .invoke(indicator, resourceId(context, appWidgetId))
                    .first()
            } ?: NetworkIndicatorReading.Failed(NO_READING)
        }

        private suspend fun resolveOnDemand(context: Context, appWidgetId: Int) {
            val indicator = NetworkMonitorWidgetIndicatorStore.read(context, appWidgetId)
            val manager = AppWidgetManager.getInstance(context)
            if (indicator != null && indicator.refresh == Refresh.ON_TAP) {
                entryPoint(context).resolveOnDemand()
                    .invoke(indicator, resourceId(context, appWidgetId), networkLabel(context))
                    .collect { reading ->
                        onDemandReadings[appWidgetId] = reading
                        render(context, manager, appWidgetId, indicator, reading)
                    }
            } else {
                updateAppWidget(context, manager, appWidgetId)
            }
        }

        /**
         * The label a measurement is filed under in the Monitor's history.
         *
         * Taken from the local-address reading, whose caption is the current SSID, rather than from the
         * snapshot repository: the widget consumes only the Phase-02 seam, and the fallback matches the
         * one the Monitor screen writes so history rows from both surfaces read the same.
         */
        private suspend fun networkLabel(context: Context): String {
            val reading = withTimeoutOrNull(FIRST_EMISSION_TIMEOUT_MS) {
                entryPoint(context).observeIndicator()
                    .invoke(NetworkMonitorIndicator.LOCAL_ADDRESS, null)
                    .first()
            }
            return (reading as? NetworkIndicatorReading.Value)?.caption ?: UNKNOWN_NETWORK
        }

        private fun resourceId(context: Context, appWidgetId: Int): Long? =
            NetworkMonitorWidgetIndicatorStore.readResourceId(context, appWidgetId)
                .takeIf { it != NetworkMonitorWidgetIndicatorStore.NO_RESOURCE_ID }

        private fun entryPoint(context: Context): NetworkMonitorWidgetEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                NetworkMonitorWidgetEntryPoint::class.java
            )
    }
}
