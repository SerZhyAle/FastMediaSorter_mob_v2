package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.ui.dialog.StreamInfoDialog
import timber.log.Timber

/**
 * S1474: owns the "about this channel" window for the streams screen, the way
 * `StreamsFilterDialogManager` owns the filter window.
 *
 * Two decisions live here rather than at the four adapter construction sites that would otherwise each
 * repeat them: the catalog health sweep is stopped before a measurement starts, because research
 * artifact 04 rules that two decoders must not run at once on a weak device; and a channel that is
 * already playing inline is read off that engine instead of being opened a second time, because a server
 * allowing one session per client would drop the sound the user is listening to.
 */
@UnstableApi
class StreamInfoDialogManager(
    private val context: Context,
    private val healthProbe: () -> StreamHealthProbeManager?,
    private val inlineAudio: () -> StreamInlineAudioManager?,
    // S1502: the outcome is no longer on the row, so the window is told it. The streams screen already
    // holds the map in a StateFlow, which is why this reads it rather than hitting the database again.
    private val playOutcome: (StreamSourceEntity) -> String? = { null },
) {

    private var dialog: StreamInfoDialog? = null

    fun show(source: StreamSourceEntity) {
        healthProbe()?.cancel()
        dismiss()
        val inline = inlineAudio()
        val engine = if (inline?.playingId == source.id) inline.playingEngine else null
        dialog = StreamInfoDialog(context, source, source.url, engine, playOutcome(source)).also { it.show() }
    }

    /** Called when the screen goes away, so a measurement never outlives the window that asked for it. */
    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}
