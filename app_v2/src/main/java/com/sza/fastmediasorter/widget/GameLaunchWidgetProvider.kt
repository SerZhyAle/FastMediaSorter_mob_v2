package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.game.GameLaunchIntents
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class GameLaunchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context.applicationContext, appWidgetManager, appWidgetIds)
    }

    companion object {
        private const val REQUEST_CODE_GAME_BASE = 31_600
        private const val REQUEST_CODE_SETTINGS_BASE = 31_700
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAll(context: Context, enabledOverride: Boolean? = null) {
            val appContext = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(appContext)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(appContext, GameLaunchWidgetProvider::class.java)
            )
            updateWidgets(appContext, appWidgetManager, appWidgetIds, enabledOverride)
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            enabled: Boolean
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_game_launch)
            views.setInt(R.id.widget_game_icon, "setImageAlpha", if (enabled) 255 else 110)
            views.setOnClickPendingIntent(
                R.id.widget_game_container,
                createPendingIntent(context, appWidgetId, enabled)
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            enabledOverride: Boolean? = null
        ) {
            if (appWidgetIds.isEmpty()) return
            widgetScope.launch {
                val enabled = enabledOverride ?: readGameEnabled(context)
                appWidgetIds.forEach { appWidgetId ->
                    updateAppWidget(context, appWidgetManager, appWidgetId, enabled)
                }
            }
        }

        private suspend fun readGameEnabled(context: Context): Boolean {
            return runCatching {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    GameWidgetEntryPoint::class.java
                ).settingsRepository().getSettings().first().embeddedGameEnabled
            }.onFailure { exception ->
                Timber.w(exception, "GameLaunchWidgetProvider: failed to read game setting")
            }.getOrDefault(false)
        }

        private fun createPendingIntent(context: Context, appWidgetId: Int, enabled: Boolean): PendingIntent {
            val intent = if (enabled) {
                GameLaunchIntents.game(context)
            } else {
                GameLaunchIntents.settingsGameToggle(context)
            }.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val requestCode = if (enabled) {
                REQUEST_CODE_GAME_BASE + appWidgetId
            } else {
                REQUEST_CODE_SETTINGS_BASE + appWidgetId
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface GameWidgetEntryPoint {
        fun settingsRepository(): SettingsRepository
    }
}