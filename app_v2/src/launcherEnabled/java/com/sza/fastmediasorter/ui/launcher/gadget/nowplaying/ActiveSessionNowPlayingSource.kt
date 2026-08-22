package com.sza.fastmediasorter.ui.launcher.gadget.nowplaying

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
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
            artwork = metadata?.let(::artworkOf),
            sourcePackage = controller.packageName,
            openIntent = controller.sessionActivity,
        )
    }

    private fun artworkOf(metadata: MediaMetadata): Bitmap? = selectArtwork(metadata::getBitmap)

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

    companion object {
        /**
         * S1177: album art first, then the generic art key, then the display icon - descending quality,
         * exactly the order the platform documents these for.
         */
        val ARTWORK_KEYS = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON,
        )

        val INACTIVE = NowPlayingState(
            active = false,
            title = "",
            artist = "",
            isPlaying = false,
            canControl = false,
        )

        /**
         * S1177: the first artwork a session actually published, tried in descending quality.
         *
         * Takes the lookup rather than the metadata object so the order can be asserted without a live
         * media session - which key a given player fills is measured per application, not answered by
         * documentation, so the order is the part that must not drift. All keys empty is an ordinary
         * outcome and yields null: the card then falls back to the source application's icon.
         */
        fun selectArtwork(lookup: (String) -> Bitmap?): Bitmap? =
            ARTWORK_KEYS.firstNotNullOfOrNull(lookup)
    }
}
