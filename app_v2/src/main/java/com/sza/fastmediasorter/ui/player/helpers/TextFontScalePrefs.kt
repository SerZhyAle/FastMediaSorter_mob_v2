package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context

/**
 * S4863: the text font size a user chose by swipe, remembered per file path so a note re-opens at the
 * size it was last read or edited at.
 *
 * Its own SharedPreferences file, read synchronously from the View layer like [DrawEditorPrefs]; Room is
 * deliberately not used because a per-file size is not worth a schema change.
 *
 * Keys are the [com.sza.fastmediasorter.domain.model.MediaFile.path] the viewer was opened with, values
 * are the size in sp. An absent entry means "never resized" and leaves the present auto-fit logic alone.
 */
object TextFontScalePrefs {

    private const val FILE_NAME = "text_font_scale"

    fun get(ctx: Context, path: String): Float? {
        if (path.isBlank()) return null
        val prefs = prefs(ctx)
        return if (prefs.contains(path)) prefs.getFloat(path, 0f) else null
    }

    fun set(ctx: Context, path: String, sizeSp: Float) {
        if (path.isBlank()) return
        prefs(ctx).edit().putFloat(path, sizeSp).apply()
    }

    /**
     * Carries the entry of a note saved for the first time to its final path: a deferred new note is typed
     * under a staging path and only gets its real one on Save, so without this the size chosen while
     * typing would be lost at the first save.
     */
    fun move(ctx: Context, fromPath: String, toPath: String) {
        if (fromPath.isBlank() || toPath.isBlank() || fromPath == toPath) return
        val size = get(ctx, fromPath) ?: return
        prefs(ctx).edit().remove(fromPath).putFloat(toPath, size).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
