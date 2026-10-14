package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.streams.ResolveBroadcastPlaybackUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ResolveBroadcastPlaybackUseCase.Playback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S4160: the Streams screen's side of a channel imported from a broadcast descriptor. Such a row is played by
 * the same launch paths as every other row, handed the address of the endpoint that answered - LAN, relay or
 * tunnel - and a failure is explained instead of reaching the player: a certificate that matches neither the
 * descriptor's pin nor the platform, or a broadcast that has ended (`DEVICE-EXCHANGE` item AF).
 */
class StreamBroadcastEndpointManager @Inject constructor(
    private val resolvePlayback: ResolveBroadcastPlaybackUseCase,
) {

    // The played address is a loopback or relay one; anything that files data by the row's own address
    // (removal, resume) must see the stored address instead.
    private val storedUrls = mutableMapOf<String, String>()

    fun needsResolution(endpoints: String?): Boolean = endpoints != null

    /** The stored address of row [id] when this manager handed out another one; else [playedUrl]. */
    fun storedUrl(id: String, playedUrl: String): String = storedUrls[id] ?: playedUrl

    /**
     * Resolves row [id] from the top of its endpoint list - a reconnect must start there (7.7) - and hands
     * [launch] the address to play, or tells the user why nothing will play.
     */
    fun play(
        scope: CoroutineScope,
        context: Context,
        id: String,
        url: String,
        endpoints: String?,
        launch: (String) -> Unit,
    ): Job = scope.launch {
        val stored = storedUrl(id, url)
        val playback = resolvePlayback(stored, endpoints)
        when (playback) {
            is Playback.Playable -> {
                storedUrls[id] = stored
                launch(playback.url)
            }
            is Playback.TrustRefused -> Toast.makeText(
                context,
                context.getString(R.string.stream_broadcast_trust_refused, playback.server),
                Toast.LENGTH_LONG,
            ).show()
            Playback.Unreachable ->
                Toast.makeText(context, R.string.stream_broadcast_ended, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        /**
         * "Through server: <host>" for a row that plays through an exchange server (strategic 3.3); null for any
         * other row, which keeps showing its address.
         */
        fun caption(context: Context, endpoints: String?): String? =
            ResolveBroadcastPlaybackUseCase.exchangeServerLabel(endpoints)?.let {
                context.getString(R.string.stream_broadcast_through_server, it)
            }
    }
}
