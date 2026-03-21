package com.sza.fastmediasorter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.browse.BrowseActivity

/**
 * Widget provider for quick resource launch
 * Allows user to configure which resource to open
 */
class ResourceLaunchWidgetProvider : AppWidgetProvider() {

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
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (appWidgetId in appWidgetIds) {
            editor.remove("resource_id_$appWidgetId")
            editor.remove("resource_name_$appWidgetId")
            editor.remove("resource_path_$appWidgetId")
            editor.remove("resource_type_$appWidgetId")
        }
        editor.apply()
    }

    override fun onEnabled(context: Context) {}

    override fun onDisabled(context: Context) {}

    companion object {

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val resourceId = prefs.getLong("resource_id_$appWidgetId", -1L)
            val resourceName = prefs.getString("resource_name_$appWidgetId", null)
            val resourcePath = prefs.getString("resource_path_$appWidgetId", null)
            val resourceTypeName = prefs.getString("resource_type_$appWidgetId", null)

            val views = RemoteViews(context.packageName, R.layout.widget_resource_launch)

            if (resourceId != -1L && resourceName != null) {
                views.setTextViewText(R.id.widget_resource_name, resourceName)

                val iconRes = resolveIcon(resourcePath, resourceTypeName)
                views.setImageViewResource(R.id.widget_resource_icon, iconRes)
                views.setInt(R.id.widget_resource_icon, "setColorFilter", Color.WHITE)

                val intent = BrowseActivity.createIntent(context, resourceId).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_resource_container, pendingIntent)
            } else {
                views.setTextViewText(
                    R.id.widget_resource_name,
                    context.getString(R.string.widget_resource_not_configured)
                )
                views.setImageViewResource(R.id.widget_resource_icon, R.drawable.ic_folder)
                views.setInt(R.id.widget_resource_icon, "setColorFilter", Color.WHITE)

                val configIntent = Intent(context, ResourceLaunchWidgetConfigActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    configIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_resource_container, pendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun resolveIcon(path: String?, typeName: String?): Int {
            // Predefined virtual resources get their own icons
            when (path) {
                LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO -> return R.drawable.ic_virtual_music
                LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO -> return R.drawable.ic_virtual_video
                LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES -> return R.drawable.ic_image
                LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS -> return R.drawable.ic_virtual_docs
                LocalMediaScanner.VIRTUAL_PATH_RECENT -> return R.drawable.ic_virtual_recent
            }
            // Resource type icons
            val type = typeName?.let { runCatching { ResourceType.valueOf(it) }.getOrNull() }
            return when (type) {
                ResourceType.SMB -> R.drawable.ic_resource_smb
                ResourceType.SFTP -> R.drawable.ic_resource_sftp
                ResourceType.FTP -> R.drawable.ic_resource_ftp
                ResourceType.CLOUD -> R.drawable.ic_resource_cloud
                else -> R.drawable.ic_resource_local
            }
        }
    }
}
