package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.widget.registry.HomeWidgetPinner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared S0661/S0669 helper that prepares and requests a per-resource home-screen widget pin.
 * The caller owns user-facing fallback UI; this manager only inspects state, stores the pending
 * widget payload, and asks the launcher to pin the widget.
 */
@Singleton
class ResourceLaunchWidgetPinManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val homeWidgetPinner: HomeWidgetPinner,
) {

    fun requestPin(
        resourceId: Long,
        resourceName: String,
        resourcePath: String,
        resourceType: ResourceType,
    ): PinResult {
        if (!homeWidgetPinner.isSupported()) return PinResult.Unsupported
        if (homeWidgetPinner.isKeyguardLocked()) return PinResult.KeyguardLocked

        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, ResourceLaunchWidgetProvider::class.java)
        val existingIds = manager.getAppWidgetIds(provider)
        val prefs = context.getSharedPreferences(ResourceLaunchWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyExists = existingIds.any { appWidgetId ->
            prefs.getLong("resource_id_$appWidgetId", -1L) == resourceId
        }
        if (alreadyExists) return PinResult.AlreadyExists

        prefs.edit()
            .putLong(ResourceLaunchWidgetProvider.KEY_PENDING_RESOURCE_ID, resourceId)
            .putString(ResourceLaunchWidgetProvider.KEY_PENDING_RESOURCE_NAME, resourceName)
            .putString(ResourceLaunchWidgetProvider.KEY_PENDING_RESOURCE_PATH, resourcePath)
            .putString(ResourceLaunchWidgetProvider.KEY_PENDING_RESOURCE_TYPE, resourceType.name)
            .apply()

        val successCallback = PendingIntent.getBroadcast(
            context,
            0,
            android.content.Intent(ResourceLaunchWidgetProvider.ACTION_WIDGET_PINNED).apply {
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val requested = homeWidgetPinner.requestPin(provider, successCallback)
        return if (requested) PinResult.Requested else PinResult.Unsupported
    }

    enum class PinResult {
        Requested,
        Unsupported,
        KeyguardLocked,
        AlreadyExists,
    }
}
