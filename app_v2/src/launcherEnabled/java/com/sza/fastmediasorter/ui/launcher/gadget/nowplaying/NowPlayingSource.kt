package com.sza.fastmediasorter.ui.launcher.gadget.nowplaying

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

data class NowPlayingState(
    val active: Boolean,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val canControl: Boolean,
)

enum class NowPlayingCommand {
    PREVIOUS,
    PLAY_PAUSE,
    NEXT,
}
