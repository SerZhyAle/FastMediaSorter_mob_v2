package com.sza.fastmediasorter.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * S1916 - per-instance configuration for the home-screen stream widget, keyed by `appWidgetId`.
 *
 * Two stores, not one, because the two kinds of value have different lifetimes and sizes: the channel's
 * identity goes into the shared widget preferences file, while its icon is a bitmap and lives as a PNG
 * under `filesDir`. Keeping the bitmap out of preferences is what stops the preferences file from
 * growing by a base64 blob per placed tile.
 *
 * [delete] removes both halves. The provider calls it from `onDeleted`, since a configuration that
 * outlives its widget is invisible: nothing ever reads it again and nothing ever cleans it up.
 */
object StreamLaunchWidgetStore {

    /** The preferences file every widget in this package shares; see [ResourceLaunchWidgetProvider]. */
    private const val PREFS_NAME = "widget_prefs"

    private const val KEY_URL_PREFIX = "stream_url_"
    private const val KEY_TITLE_PREFIX = "stream_title_"
    private const val KEY_MEDIA_KIND_PREFIX = "stream_media_kind_"

    private const val ICON_DIR = "stream_widget"
    private const val PNG_QUALITY = 100

    /** What a placed tile needs to draw itself and to build its launch intent. */
    data class Config(
        val url: String,
        val title: String,
        val mediaKind: String,
    )

    fun save(
        context: Context,
        appWidgetId: Int,
        url: String,
        title: String,
        mediaKind: String,
        iconTile: Bitmap?,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL_PREFIX + appWidgetId, url)
            .putString(KEY_TITLE_PREFIX + appWidgetId, title)
            .putString(KEY_MEDIA_KIND_PREFIX + appWidgetId, mediaKind)
            .apply()
        if (iconTile != null) {
            writeIcon(context, appWidgetId, iconTile)
        }
    }

    fun read(context: Context, appWidgetId: Int): Config? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_URL_PREFIX + appWidgetId, null)
        val title = prefs.getString(KEY_TITLE_PREFIX + appWidgetId, null)
        // A tile with no url cannot launch anything, so a half-written record reads as unconfigured
        // rather than as a tile that silently does nothing when tapped.
        if (url.isNullOrBlank() || title == null) {
            return null
        }
        val mediaKind = prefs.getString(KEY_MEDIA_KIND_PREFIX + appWidgetId, null).orEmpty()
        return Config(url = url, title = title, mediaKind = mediaKind)
    }

    fun delete(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_URL_PREFIX + appWidgetId)
            .remove(KEY_TITLE_PREFIX + appWidgetId)
            .remove(KEY_MEDIA_KIND_PREFIX + appWidgetId)
            .apply()
        val file = iconFile(context, appWidgetId)
        if (file.exists() && !file.delete()) {
            Timber.w("StreamLaunchWidgetStore: could not delete icon for widget %d", appWidgetId)
        }
    }

    fun iconFile(context: Context, appWidgetId: Int): File =
        File(File(context.filesDir, ICON_DIR), "$appWidgetId.png")

    /**
     * Decoded on demand rather than cached: a widget update runs in a short-lived receiver process, so a
     * cache would never be warm and would only hold the bitmap alive past the update it was read for.
     */
    fun readIcon(context: Context, appWidgetId: Int): Bitmap? {
        val file = iconFile(context, appWidgetId)
        if (!file.exists()) {
            return null
        }
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }
            .onFailure { Timber.w(it, "StreamLaunchWidgetStore: undecodable icon for widget %d", appWidgetId) }
            .getOrNull()
    }

    private fun writeIcon(context: Context, appWidgetId: Int, tile: Bitmap) {
        val file = iconFile(context, appWidgetId)
        file.parentFile?.mkdirs()
        runCatching {
            FileOutputStream(file).use { out -> tile.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out) }
        }.onFailure { Timber.w(it, "StreamLaunchWidgetStore: could not write icon for widget %d", appWidgetId) }
    }
}
