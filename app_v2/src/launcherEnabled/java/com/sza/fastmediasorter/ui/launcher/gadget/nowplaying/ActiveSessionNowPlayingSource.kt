package com.sza.fastmediasorter.ui.launcher.gadget.nowplaying

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import timber.log.Timber

/**
 * S0429: what any other app is playing, read through the platform's active media session.
 *
 * Provider-agnostic by construction - no package name is matched and no vendor API is called - so a
 * player this was never written for works exactly like the ones it was.
 */
class ActiveSessionNowPlayingSource(private val context: Context) : NowPlayingSource {

    override fun read(): NowPlayingState {
        val controller = activeController() ?: return INACTIVE
        val metadata = controller.metadata
        return NowPlayingState(
            active = true,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            isPlaying = controller.isPlaying(),
            canControl = true,
        )
    }

    override fun send(command: NowPlayingCommand): Boolean {
        val controller = activeController() ?: return false
        return runCatching {
            when (command) {
                NowPlayingCommand.PREVIOUS -> controller.transportControls.skipToPrevious()
                NowPlayingCommand.NEXT -> controller.transportControls.skipToNext()
                NowPlayingCommand.PLAY_PAUSE -> if (controller.isPlaying()) {
                    controller.transportControls.pause()
                } else {
                    controller.transportControls.play()
                }
            }
        }.isSuccess
    }

    /**
     * The session a user would call "what is playing": the one actually playing, or failing that the
     * first that exists at all, so a paused player still fills the gadget instead of emptying it.
     */
    private fun activeController(): MediaController? {
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val component = ComponentName(context, MediaSessionAccessService::class.java)
        // The platform throws when access is revoked between the check and this call, and the right
        // answer then is an empty gadget rather than a crash on the home screen. Only the failure kind
        // is logged - never the track, the artist or the app it came from.
        val controllers = runCatching { manager.getActiveSessions(component) }
            .onFailure { Timber.w("Now Playing: session query refused (%s)", it.javaClass.simpleName) }
            .getOrNull()
            .orEmpty()
        return controllers.firstOrNull { it.isPlaying() } ?: controllers.firstOrNull()
    }

    private fun MediaController.isPlaying(): Boolean =
        playbackState?.state == PlaybackState.STATE_PLAYING

    private companion object {
        val INACTIVE = NowPlayingState(
            active = false,
            title = "",
            artist = "",
            isPlaying = false,
            canControl = false,
        )
    }
}
