package com.sza.fastmediasorter.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Lightweight persistent state for every Random Photo Frame widget instance.
 *
 * The configuration activity and refresh path own writes. The widget provider only reads and
 * renders the current snapshot.
 */
object RandomPhotoFrameSnapshotStore {
    private const val PREFS = "random_photo_frame_widget"
    private const val KEY_RESOURCE_ID = "resource_id"
    private const val KEY_RESOURCE_NAME = "resource_name"
    private const val KEY_SELECTED_FILE_PATH = "selected_file_path"
    private const val KEY_SELECTED_THUMBNAIL_URI = "selected_thumbnail_uri"
    private const val KEY_HAS_RENDERABLE_PHOTO = "has_renderable_photo"
    private const val KEY_FALLBACK_MESSAGE = "fallback_message"

    data class Snapshot(
        val resourceId: Long = -1L,
        val resourceName: String = "",
        val selectedFilePath: String = "",
        val selectedThumbnailUri: String = "",
        val hasRenderablePhoto: Boolean = false,
        val fallbackMessage: String = ""
    ) {
        val isConfigured: Boolean
            get() = resourceId > 0L
    }

    fun read(context: Context, appWidgetId: Int): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            resourceId = prefs.getLong(namespaced(KEY_RESOURCE_ID, appWidgetId), -1L),
            resourceName = prefs.getString(namespaced(KEY_RESOURCE_NAME, appWidgetId), "").orEmpty(),
            selectedFilePath = prefs.getString(namespaced(KEY_SELECTED_FILE_PATH, appWidgetId), "").orEmpty(),
            selectedThumbnailUri = prefs.getString(namespaced(KEY_SELECTED_THUMBNAIL_URI, appWidgetId), "").orEmpty(),
            hasRenderablePhoto = prefs.getBoolean(namespaced(KEY_HAS_RENDERABLE_PHOTO, appWidgetId), false),
            fallbackMessage = prefs.getString(namespaced(KEY_FALLBACK_MESSAGE, appWidgetId), "").orEmpty(),
        )
    }

    fun write(
        context: Context,
        appWidgetId: Int,
        snapshot: Snapshot,
        notifyWidgets: Boolean = true
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(namespaced(KEY_RESOURCE_ID, appWidgetId), snapshot.resourceId)
            .putString(namespaced(KEY_RESOURCE_NAME, appWidgetId), snapshot.resourceName)
            .putString(namespaced(KEY_SELECTED_FILE_PATH, appWidgetId), snapshot.selectedFilePath)
            .putString(namespaced(KEY_SELECTED_THUMBNAIL_URI, appWidgetId), snapshot.selectedThumbnailUri)
            .putBoolean(namespaced(KEY_HAS_RENDERABLE_PHOTO, appWidgetId), snapshot.hasRenderablePhoto)
            .putString(namespaced(KEY_FALLBACK_MESSAGE, appWidgetId), snapshot.fallbackMessage)
            .apply()
        if (notifyWidgets) {
            updateWidgets(context, intArrayOf(appWidgetId))
        }
    }

    fun clear(context: Context, appWidgetId: Int, notifyWidgets: Boolean = true) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(namespaced(KEY_RESOURCE_ID, appWidgetId))
            .remove(namespaced(KEY_RESOURCE_NAME, appWidgetId))
            .remove(namespaced(KEY_SELECTED_FILE_PATH, appWidgetId))
            .remove(namespaced(KEY_SELECTED_THUMBNAIL_URI, appWidgetId))
            .remove(namespaced(KEY_HAS_RENDERABLE_PHOTO, appWidgetId))
            .remove(namespaced(KEY_FALLBACK_MESSAGE, appWidgetId))
            .apply()
        if (notifyWidgets) {
            updateWidgets(context, intArrayOf(appWidgetId))
        }
    }

    fun updateWidgets(context: Context, appWidgetIds: IntArray? = null) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = appWidgetIds ?: manager.getAppWidgetIds(
            ComponentName(context, RandomPhotoFrameWidgetProvider::class.java)
        )
        ids.forEach { id ->
            RandomPhotoFrameWidgetProvider.updateAppWidget(context, manager, id)
        }
    }

    private fun namespaced(key: String, appWidgetId: Int): String = "${key}_$appWidgetId"
}