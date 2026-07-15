package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.ui.streams.StreamsActivity

/**
 * S0637: builds and requests a pinned home-screen shortcut for one stream source. The shortcut's tap
 * intent reopens the Streams screen and plays that channel. Kept out of the Activity (Rule 3);
 * instantiate with the Activity context at the call site - no Hilt binding needed.
 */
class StreamShortcutPinManager(private val context: Context) {

    /**
     * Requests the launcher to pin a shortcut for [source]. Returns false when the launcher does not
     * support pinning (API < 26 or an unsupported launcher), so the caller can show a message.
     *
     * S1067: [iconBitmap] is the channel's favicon tile from the sprite-atlas when available; it is
     * used as the shortcut icon so the pin matches the channel's list/grid thumbnail instead of the
     * generic media-kind vector. Null (channel has no atlas favicon) falls back to that vector.
     * createWithBitmap (legacy) not createWithAdaptiveBitmap - adaptive masks the outer 25% and would
     * crop the square favicon.
     */
    fun requestPin(source: StreamSourceEntity, iconBitmap: Bitmap? = null): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val icon = iconBitmap?.let { IconCompat.createWithBitmap(it) }
            ?: IconCompat.createWithResource(context, iconFor(source.mediaKind))
        val info = ShortcutInfoCompat.Builder(context, "stream_${source.id}")
            .setShortLabel(source.title)
            .setLongLabel(source.title)
            .setIcon(icon)
            .setIntent(StreamsActivity.createPlayShortcutIntent(context, source.url))
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    // Mirrors StreamSourceAdapter.kindIcon so the shortcut icon matches the channel's row icon.
    private fun iconFor(mediaKind: String): Int =
        if (mediaKind == "AUDIO") R.drawable.ic_audio else R.drawable.ic_video
}
