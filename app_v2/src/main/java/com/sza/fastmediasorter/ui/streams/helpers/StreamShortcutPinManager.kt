package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
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
     */
    fun requestPin(source: StreamSourceEntity): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val info = ShortcutInfoCompat.Builder(context, "stream_${source.id}")
            .setShortLabel(source.title)
            .setLongLabel(source.title)
            .setIcon(IconCompat.createWithResource(context, iconFor(source.mediaKind)))
            .setIntent(StreamsActivity.createPlayShortcutIntent(context, source.url))
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    // Mirrors StreamSourceAdapter.kindIcon so the shortcut icon matches the channel's row icon.
    private fun iconFor(mediaKind: String): Int =
        if (mediaKind == "AUDIO") R.drawable.ic_audio else R.drawable.ic_video
}
