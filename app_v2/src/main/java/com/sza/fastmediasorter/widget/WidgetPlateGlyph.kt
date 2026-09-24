package com.sza.fastmediasorter.widget

import android.content.ContentResolver
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException

/**
 * A glyph for a home-widget image slot that also shows pictures (album art, a stream's favicon).
 *
 * The slot cannot carry a view tint - it would repaint the picture - and the glyph's own theme tint
 * resolves against the launcher's theme, not ours, so a light theme would leave a dark glyph on the
 * dark widget plate. The colour is therefore put on the icon itself, resolved in our process.
 */
object WidgetPlateGlyph {

    fun icon(context: Context, @DrawableRes glyphRes: Int): Icon {
        Timber.d("S3430: widget picture slot glyph tinted for the plate")
        return Icon.createWithResource(context, glyphRes)
            .setTint(ContextCompat.getColor(context, R.color.white))
    }

    /**
     * The picture's URI when the launcher will be able to draw it, else null.
     *
     * `RemoteViews.setImageViewUri` reports nothing back: a remote URL or a cache file that is gone
     * is drawn as an empty slot on the launcher's side. The caller shows the glyph instead
     * (ICON-EXTERNAL rule 4), so the check has to happen here, before the URI leaves the process.
     */
    fun drawableUriOrNull(context: Context, raw: String): Uri? {
        if (raw.isBlank()) return null
        val uri = Uri.parse(raw)
        val readable = when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).canRead() } == true
            ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_ANDROID_RESOURCE -> opensForReading(context, uri)
            else -> false
        }
        return uri.takeIf { readable }
    }

    private fun opensForReading(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
    } catch (e: FileNotFoundException) {
        Timber.w(e, "Widget picture is not readable, the glyph stands in")
        false
    } catch (e: SecurityException) {
        Timber.w(e, "Widget picture is not readable, the glyph stands in")
        false
    }
}
