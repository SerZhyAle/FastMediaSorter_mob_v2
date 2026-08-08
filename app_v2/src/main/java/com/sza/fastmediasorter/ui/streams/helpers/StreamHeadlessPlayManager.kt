package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.ui.player.helpers.AudioServiceController
import timber.log.Timber

/**
 * S1471: starts or stops one radio stream through the audio foreground service with no view attached,
 * so a pinned shortcut can play a channel without building the Streams screen first.
 *
 * [StreamInlineAudioManager] cannot serve this path: it owns the visible mini-control and its play()
 * binds to that view. Only [AudioServiceController] is view-free, and this class is the caller that
 * drives it.
 *
 * Ownership: the started controller is deliberately left connected on the play branch. The session it
 * just started outlives the trampoline that called this, and releasing the controller immediately
 * would let the service quiesce before playback reaches the foreground state. The binding dies with
 * the process, which is also when the playback it represents ends. The stop branch releases, because
 * nothing remains to keep alive.
 */
class StreamHeadlessPlayManager(context: Context) {

    /**
     * Application context, not the caller's. The trampoline that calls this finishes immediately while
     * the play branch keeps the controller connected on purpose, so binding against the Activity would
     * outlive it and hold it alive. Media3 builds its controller against any context, and the process
     * scope is the one that matches how long this connection is meant to live.
     */
    private val context: Context = context.applicationContext

    /**
     * Plays [source] through the service, or stops it when that same channel is already playing -
     * the toggle a tap on the channel's row performs on the Streams screen (S0690). [onFinished] runs
     * once the branch is decided and acted on, so the caller can close itself.
     */
    fun play(source: StreamSourceEntity, onFinished: () -> Unit) {
        val controller = AudioServiceController(context)
        controller.connect { player ->
            val playingUrl = player.currentMediaItem?.requestMetadata?.mediaUri?.toString()
            if (playingUrl == source.url && player.isPlaying) {
                Timber.i("StreamHeadlessPlayManager: stop already-playing %s", source.url)
                player.stop()
                controller.release()
                onFinished()
            } else {
                Timber.i("StreamHeadlessPlayManager: start %s", source.url)
                controller.playAudioWithMetadata(Uri.parse(source.url), source.title) { onFinished() }
            }
        }
    }

    companion object {

        /**
         * Whether [source] can play without a screen at all. Every false arm is a case strategic §3.1
         * pins to today's behaviour - open the Streams screen and let it decide.
         */
        fun qualifiesForHeadlessPlay(
            source: StreamSourceEntity,
            backgroundAudioEnabled: Boolean,
            hasNetwork: Boolean,
        ): Boolean = source.mediaKind == AUDIO_MEDIA_KIND && backgroundAudioEnabled && hasNetwork

        private const val AUDIO_MEDIA_KIND = "AUDIO"
    }
}
