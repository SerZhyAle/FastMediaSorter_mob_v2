package com.sza.fastmediasorter.widget.registry

import android.app.KeyguardManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared primitives for pinning an app widget to the home screen via the system
 * `requestPinAppWidget` flow. Used by the settings widget picker and the resource editor.
 *
 * The `Build.VERSION.SDK_INT >= O` guard is inlined in every method that calls an API 26 API so
 * the `legacy` flavor (minSdk 23) stays safe and lint flow-analysis sees the guard locally.
 */
@Singleton
class HomeWidgetPinner @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** True when the platform and the current launcher support programmatic widget pinning. */
    fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun isKeyguardLocked(): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /**
     * Requests the launcher to pin [component]. Returns false (without touching the system) when
     * pinning is unsupported, so the caller can show the unsupported fallback message.
     */
    fun requestPin(component: ComponentName, successCallback: PendingIntent?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(component, null, successCallback)
    }
}
