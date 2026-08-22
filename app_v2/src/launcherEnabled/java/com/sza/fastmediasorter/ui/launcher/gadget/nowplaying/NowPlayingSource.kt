package com.sza.fastmediasorter.ui.launcher.gadget.nowplaying

import android.app.PendingIntent
import android.graphics.Bitmap

/**
 * S0429: where the launcher's Now Playing gadget reads what is playing, and where its buttons go.
 *
 * The gadget has two possible sources - this app's own playback service, and whatever app currently
 * holds the active media session once the user grants notification access - and must not know which
 * one it is talking to, so that the choice can be re-made on every render.
 */
interface NowPlayingSource {

    fun read(): NowPlayingState

    /**
     * @return whether the command reached a session. The gadget hides its transport row when it did
     * not, rather than leaving three buttons that do nothing.
     */
    fun send(command: NowPlayingCommand): Boolean
}

/**
 * What the gadget draws.
 *
 * S1177: the three fields below the original five default to the empty case, and a source fills only what
 * it has. The card is required to render with any subset of them - the own-playback source knows no
 * foreign package and has no session activity to hand over, while a foreign session may carry all three or
 * none, depending on what the playing application chose to publish.
 */
data class NowPlayingState(
    val active: Boolean,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val canControl: Boolean,
    /** Cover art as published by the session, or null when the session published none. */
    val artwork: Bitmap? = null,
    /** Which application owns the session, so the card can fall back to its icon and name it. */
    val sourcePackage: String? = null,
    /** The owning player's own entry point, so a tap can open the app that is actually playing. */
    val openIntent: PendingIntent? = null,
)

enum class NowPlayingCommand {
    PREVIOUS,
    PLAY_PAUSE,
    NEXT,
}
