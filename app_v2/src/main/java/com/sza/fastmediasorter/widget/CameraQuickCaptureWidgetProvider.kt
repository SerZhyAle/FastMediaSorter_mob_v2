package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sza.fastmediasorter.R

/**
 * S0369 - compact 1x1 home-screen widget that captures a quick photo and saves it into a
 * configuration-bound target (a writable non-virtual resource or the device camera folder).
 *
 * A tap on a configured instance opens the transparent [CameraQuickCaptureActivity] trampoline,
 * which owns the CAMERA runtime permission and the capture/save flow (Rule 3 - no logic here). An
 * unconfigured instance opens [CameraQuickCaptureConfigActivity] so the user can pick a target.
 *
 * Per-instance target state lives in the shared `widget_prefs` file under a `cam_capture_*`
 * key namespace ([keyTargetId] etc.); [onDeleted] clears those keys for removed instances.
 *
 * Flavor availability is decided by the merged manifest (receiver removed where camera capture is
 * not meaningful); no `BuildConfig.SUPPORT_*` is read here (Rule 15).
 */
class CameraQuickCaptureWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (appWidgetId in appWidgetIds) {
            editor.remove(keyTargetId(appWidgetId))
            editor.remove(keyTargetName(appWidgetId))
            editor.remove(keyTargetPath(appWidgetId))
            editor.remove(keyTargetType(appWidgetId))
            editor.remove(keyTargetIsCameraFolder(appWidgetId))
            editor.remove(keyCaptureMode(appWidgetId))
        }
        editor.apply()
    }

    companion object {

        const val PREFS_NAME = "widget_prefs"

        // Distinct key namespace so this widget never collides with ResourceLaunch's resource_* keys.
        fun keyTargetId(id: Int) = "cam_capture_target_id_$id"
        fun keyTargetName(id: Int) = "cam_capture_target_name_$id"
        fun keyTargetPath(id: Int) = "cam_capture_target_path_$id"
        fun keyTargetType(id: Int) = "cam_capture_target_type_$id"
        fun keyTargetIsCameraFolder(id: Int) = "cam_capture_target_is_camera_folder_$id"
        // S0371: per-instance capture mode ("photo" default / "video"); same cam_capture_* namespace.
        fun keyCaptureMode(id: Int) = "cam_capture_mode_$id"

        const val CAPTURE_MODE_PHOTO = "photo"
        const val CAPTURE_MODE_VIDEO = "video"

        /** Persisted capture mode for [appWidgetId]; defaults to photo for instances pinned pre-S0371. */
        fun captureMode(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(keyCaptureMode(appWidgetId), CAPTURE_MODE_PHOTO) ?: CAPTURE_MODE_PHOTO
        }

        /** True once a target (resource or camera folder) is bound to [appWidgetId]. */
        fun isConfigured(context: Context, appWidgetId: Int): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(keyTargetIsCameraFolder(appWidgetId), false) ||
                prefs.getLong(keyTargetId(appWidgetId), -1L) != -1L
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_camera_quick_capture)
            views.setImageViewResource(
                R.id.widget_camera_quick_capture_icon,
                R.drawable.ic_widget_camera_quick_capture_accent
            )

            val pendingIntent = if (isConfigured(context, appWidgetId)) {
                val intent = Intent(context, CameraQuickCaptureActivity::class.java).apply {
                    action = CameraQuickCaptureActivity.ACTION_CAPTURE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    // Unique data so distinct instances do not share one cached PendingIntent.
                    data = android.net.Uri.parse("fms://cam-capture/$appWidgetId")
                }
                PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                val configIntent = Intent(context, CameraQuickCaptureConfigActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    configIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            views.setOnClickPendingIntent(R.id.widget_camera_quick_capture_container, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
