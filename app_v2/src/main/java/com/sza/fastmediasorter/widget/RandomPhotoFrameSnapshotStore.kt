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

    /**
     * S1170: who a snapshot belongs to. A home-screen widget is identified by its `appWidgetId`; a
     * launcher desktop cell has no widget id and never will, so it needs its own namespace.
     *
     * **[Widget.token] must stay byte-identical forever.** It is the suffix already written into
     * `random_photo_frame_widget` prefs on every device that has a configured frame; changing it would
     * orphan those users' photo frames silently - the widget would render as unconfigured and the old
     * keys would linger. That is why a widget's token is the bare number it has always been, and the
     * cell namespace gets a letter prefix instead: `"7"` and `"cell7"` can never collide, whereas two
     * numeric spaces would.
     */
    sealed interface SnapshotOwner {
        val token: String

        data class Widget(val appWidgetId: Int) : SnapshotOwner {
            override val token: String get() = appWidgetId.toString()
        }

        /**
         * S1930: [instanceId] is the launcher's own token from [LauncherWidgetToken], not the desktop
         * cell's row id - the add flow configures a cell before inserting it, so no row id exists yet.
         */
        data class LauncherCell(val instanceId: Long) : SnapshotOwner {
            override val token: String get() = "$CELL_PREFIX$instanceId"
        }

        companion object {
            private const val CELL_PREFIX = "cell"
        }
    }

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

    fun read(context: Context, owner: SnapshotOwner): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            resourceId = prefs.getLong(namespaced(KEY_RESOURCE_ID, owner), -1L),
            resourceName = prefs.getString(namespaced(KEY_RESOURCE_NAME, owner), "").orEmpty(),
            selectedFilePath = prefs.getString(namespaced(KEY_SELECTED_FILE_PATH, owner), "").orEmpty(),
            selectedThumbnailUri = prefs.getString(namespaced(KEY_SELECTED_THUMBNAIL_URI, owner), "").orEmpty(),
            hasRenderablePhoto = prefs.getBoolean(namespaced(KEY_HAS_RENDERABLE_PHOTO, owner), false),
            fallbackMessage = prefs.getString(namespaced(KEY_FALLBACK_MESSAGE, owner), "").orEmpty(),
        )
    }

    fun write(
        context: Context,
        owner: SnapshotOwner,
        snapshot: Snapshot,
        notifyWidgets: Boolean = true
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(namespaced(KEY_RESOURCE_ID, owner), snapshot.resourceId)
            .putString(namespaced(KEY_RESOURCE_NAME, owner), snapshot.resourceName)
            .putString(namespaced(KEY_SELECTED_FILE_PATH, owner), snapshot.selectedFilePath)
            .putString(namespaced(KEY_SELECTED_THUMBNAIL_URI, owner), snapshot.selectedThumbnailUri)
            .putBoolean(namespaced(KEY_HAS_RENDERABLE_PHOTO, owner), snapshot.hasRenderablePhoto)
            .putString(namespaced(KEY_FALLBACK_MESSAGE, owner), snapshot.fallbackMessage)
            .apply()
        notifyOwner(context, owner, notifyWidgets)
    }

    fun clear(context: Context, owner: SnapshotOwner, notifyWidgets: Boolean = true) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(namespaced(KEY_RESOURCE_ID, owner))
            .remove(namespaced(KEY_RESOURCE_NAME, owner))
            .remove(namespaced(KEY_SELECTED_FILE_PATH, owner))
            .remove(namespaced(KEY_SELECTED_THUMBNAIL_URI, owner))
            .remove(namespaced(KEY_HAS_RENDERABLE_PHOTO, owner))
            .remove(namespaced(KEY_FALLBACK_MESSAGE, owner))
            .apply()
        notifyOwner(context, owner, notifyWidgets)
    }

    // Instance-id overloads, kept so every existing call site stays byte-identical in behaviour.
    fun read(context: Context, appWidgetId: Int): Snapshot =
        read(context, ownerFor(appWidgetId))

    fun write(context: Context, appWidgetId: Int, snapshot: Snapshot, notifyWidgets: Boolean = true) =
        write(context, ownerFor(appWidgetId), snapshot, notifyWidgets)

    fun clear(context: Context, appWidgetId: Int, notifyWidgets: Boolean = true) =
        clear(context, ownerFor(appWidgetId), notifyWidgets)

    /**
     * S1930: the configuration chain - config screen, refresher, cleanup - carries a bare `Int` and the
     * launcher reuses that chain unchanged, so the id's range is the only thing that says who minted it.
     * Resolving here rather than at each call site is what keeps the two writers on one namespace: the
     * config screen and the gadget reach the same keys without either knowing about the other.
     */
    private fun ownerFor(appWidgetId: Int): SnapshotOwner =
        if (LauncherWidgetToken.isLauncherToken(appWidgetId)) {
            SnapshotOwner.LauncherCell(appWidgetId.toLong())
        } else {
            SnapshotOwner.Widget(appWidgetId)
        }

    /**
     * Only a real widget has a host to notify. A launcher cell redraws from its own view, and asking
     * AppWidgetManager to update a cell id would poke whatever widget happens to hold that number.
     */
    private fun notifyOwner(context: Context, owner: SnapshotOwner, notifyWidgets: Boolean) {
        if (!notifyWidgets) return
        if (owner is SnapshotOwner.Widget) {
            updateWidgets(context, intArrayOf(owner.appWidgetId))
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

    /**
     * For a widget owner this produces exactly the string the previous `"${key}_$appWidgetId"` did -
     * that identity is the whole point, and the reason [SnapshotOwner.Widget.token] is a bare number.
     */
    private fun namespaced(key: String, owner: SnapshotOwner): String = "${key}_${owner.token}"
}