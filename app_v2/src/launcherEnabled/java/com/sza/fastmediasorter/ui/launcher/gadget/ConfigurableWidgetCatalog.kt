package com.sza.fastmediasorter.ui.launcher.gadget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.widget.CameraQuickCaptureConfigActivity
import com.sza.fastmediasorter.widget.CameraQuickCaptureWidgetProvider
import com.sza.fastmediasorter.widget.LauncherWidgetToken
import com.sza.fastmediasorter.widget.RandomPhotoFrameConfigActivity
import com.sza.fastmediasorter.widget.RandomPhotoFrameSnapshotStore

/**
 * S1930: the home-screen widgets whose cell owns a configured instance, and the three things the
 * launcher has to know about such a widget - that it is one, how to open its configuration screen, and
 * how to throw that instance away again.
 *
 * One table rather than three `when`s spread over the add flow, the gadget classes and the removal
 * path: strategic §5.3 makes "a third configurable widget is one registration" an acceptance criterion,
 * and three call sites branching on the same two keys is exactly the shape that makes the third widget
 * cost three edits and be forgotten in one of them.
 *
 * The launcher half stops here. Neither widget knows a launcher exists: both take a plain `Int`
 * instance id, and [LauncherWidgetToken] is what makes the one this file hands them unreachable to the
 * platform's own allocator.
 */
object ConfigurableWidgetCatalog {

    /** True when a cell of [gadgetKey] must be configured before it can be placed. */
    fun isConfigurable(gadgetKey: String): Boolean = gadgetKey in CONFIG_SCREENS

    /**
     * The widget's own configuration screen, aimed at [token] - the same Activity the system home
     * screen opens, which is what owner wish §3.1 asks for. Null for a gadget that configures nothing.
     */
    fun configIntent(context: Context, gadgetKey: String, token: Int): Intent? {
        val screen = CONFIG_SCREENS[gadgetKey] ?: return null
        return Intent(context, screen).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, token)
    }

    /**
     * Drops everything [token] had stored, through the same call each widget's own `onDeleted` makes.
     * A gadget that configures nothing, or a param that never held a token, is a no-op - the removal
     * path calls this for every cell it deletes and must not have to ask first.
     */
    fun clearInstance(context: Context, gadgetKey: String, token: Int) {
        if (!LauncherWidgetToken.isLauncherToken(token)) return
        when (gadgetKey) {
            LauncherGadgetRegistry.KEY_RANDOM_PHOTO_FRAME ->
                RandomPhotoFrameSnapshotStore.clear(context, token, notifyWidgets = false)

            LauncherGadgetRegistry.KEY_CAMERA_QUICK_CAPTURE ->
                CameraQuickCaptureWidgetProvider.clearInstanceConfig(context, token)
        }
    }

    /**
     * The token a placed cell carries, or null when this cell holds no configured instance. The param
     * is read back through [LauncherWidgetToken.isLauncherToken] rather than trusted: a `target` column
     * is user-visible storage that survives restores and hand edits, and a stray positive number in it
     * would otherwise address a stranger's home-screen widget.
     */
    fun tokenOf(param: String?): Int? =
        param?.toIntOrNull()?.takeIf { LauncherWidgetToken.isLauncherToken(it) }

    private val CONFIG_SCREENS: Map<String, Class<out Activity>> = mapOf(
        LauncherGadgetRegistry.KEY_RANDOM_PHOTO_FRAME to RandomPhotoFrameConfigActivity::class.java,
        LauncherGadgetRegistry.KEY_CAMERA_QUICK_CAPTURE to CameraQuickCaptureConfigActivity::class.java,
    )
}
