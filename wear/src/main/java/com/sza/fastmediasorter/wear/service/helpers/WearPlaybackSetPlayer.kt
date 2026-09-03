package com.sza.fastmediasorter.wear.service.helpers

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager

/**
 * S2166: makes NEXT and PREVIOUS reachable from the minimized session.
 *
 * The service's player holds one item at a time, so Media3 would report both commands unavailable
 * and the notification would draw neither button. The set the browse screen published is what knows
 * what follows, exactly as it does for the player screen, so the two cannot disagree about the
 * successor - re-querying the repository is not an alternative for the reason S1683 ADR-2 records:
 * a fresh query answers in a different order than the sorted or filtered list the user saw.
 */
class WearPlaybackSetPlayer(
    player: Player,
    private val playbackSetManager: PlaybackSetManager
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS)
            .build()

    override fun isCommandAvailable(command: Int): Boolean =
        command == Player.COMMAND_SEEK_TO_NEXT ||
            command == Player.COMMAND_SEEK_TO_PREVIOUS ||
            super.isCommandAvailable(command)

    override fun seekToNext() = play(playbackSetManager.next())

    override fun seekToNextMediaItem() = seekToNext()

    override fun hasNextMediaItem(): Boolean = playbackSetManager.currentSet.value != null

    /**
     * A press early in a track restarts it rather than leaving the set, which is what every media
     * notification on the platform does and what the set's own wrap rule would otherwise override.
     */
    override fun seekToPrevious() {
        if (currentPosition > maxSeekToPreviousPosition) {
            seekTo(0)
            return
        }
        play(playbackSetManager.previous())
    }

    override fun seekToPreviousMediaItem() = play(playbackSetManager.previous())

    override fun hasPreviousMediaItem(): Boolean = playbackSetManager.currentSet.value != null

    private fun play(file: WearMediaFile?) {
        if (file == null) {
            return
        }
        setMediaItem(MediaItem.fromUri(file.uri))
        prepare()
        play()
    }
}
