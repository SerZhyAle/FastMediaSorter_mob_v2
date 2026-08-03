package com.sza.fastmediasorter.ui.streams.helpers

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity

/**
 * Constructor holders for [StreamInlineAudioManager]. Grouping the view handles and the host callbacks
 * keeps its constructor at four parameters instead of ten, below detekt's `LongParameterList`
 * threshold and without a suppression. Both are data types, which the rule exempts by default.
 */
data class StreamInlineAudioViews(
    val miniControl: View,
    val titleView: TextView,
    val playStopButton: ImageButton,
)

/**
 * Every field defaults to a no-op, so a host that only cares about a subset supplies just that subset.
 */
data class StreamInlineAudioCallbacks(
    val onPlayingChanged: (String?) -> Unit = {},
    val onPlaybackStateChanged: (StreamSourceEntity?, Boolean) -> Unit = { _, _ -> },
    // Fired when the inline stream fails to play (no response, dead URL). The Activity surfaces a
    // dialog offering retry / remove-from-list - without this hook the failure only stopped the
    // background service and left the UI silent.
    val onError: (StreamSourceEntity) -> Unit = {},
    // S0593: fired once per play when the stream actually starts playing (ground-truth "OK" outcome).
    // The Activity forwards it to the ViewModel, which records the green status for this source.
    val onSuccess: (StreamSourceEntity) -> Unit = {},
    // S1142: fired with this manager's own playing id and the current now-playing track line (or null)
    // so the Activity can mirror it onto the active channel's grid tile (the grid adapters have no
    // metadata pipeline of their own). The id is passed in rather than read back from the Activity's
    // reference to this manager: the init-time StateFlow emit fires synchronously inside the constructor,
    // before that lateinit field is assigned, so reading it back crashed (UninitializedPropertyAccess).
    val onNowPlayingChanged: (playingId: String?, track: String?) -> Unit = { _, _ -> },
)
