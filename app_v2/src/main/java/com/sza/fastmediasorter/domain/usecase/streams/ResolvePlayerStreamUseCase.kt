package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.domain.player.BroadcastPlaybackSessions
import com.sza.fastmediasorter.domain.usecase.streams.ResolveBroadcastPlaybackUseCase.Playback
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

/** Resolves a selected catalog row at connection time, keeping temporary bridge URLs out of the playlist. */
class ResolvePlayerStreamUseCase @Inject constructor(
    private val getSource: GetStreamSourceByUrlUseCase,
    private val resolveBroadcast: ResolveBroadcastPlaybackUseCase,
) {
    suspend operator fun invoke(path: String): Playback {
        val source = getSource(path)?.takeIf { it.broadcastEndpoints != null } ?: return Playback.Playable(path)
        val playback = resolveBroadcast(source.url, source.broadcastEndpoints, source.id)
        currentCoroutineContext().ensureActive()
        if (playback is Playback.Playable) BroadcastPlaybackSessions.register(playback.url, source)
        return playback
    }
}
