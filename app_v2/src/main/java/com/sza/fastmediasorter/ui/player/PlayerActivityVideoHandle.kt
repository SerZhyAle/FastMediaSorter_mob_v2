package com.sza.fastmediasorter.ui.player

import androidx.media3.common.Player
import com.sza.fastmediasorter.ui.player.contracts.VideoPlayerHandle

internal class PlayerActivityVideoHandle(
    private val videoPlayerManagerProvider: () -> VideoPlayerManager?,
    private val audioPlayerProvider: () -> Player?,
    private val isAudioServiceActive: () -> Boolean,
) : VideoPlayerHandle {
    override fun getAvailableAudioTracks(): List<VideoTrackSelectionManager.TrackInfo> =
        videoPlayerManagerProvider()?.getAvailableAudioTracks()
            ?.map { VideoTrackSelectionManager.TrackInfo(it.groupIndex, it.trackIndex, it.label, it.isSelected) }
            ?: emptyList()

    override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) =
        videoPlayerManagerProvider()?.selectAudioTrack(groupIndex, trackIndex) ?: Unit

    override fun getAvailableSubtitleTracks(): List<VideoTrackSelectionManager.TrackInfo> =
        videoPlayerManagerProvider()?.getAvailableSubtitleTracks()
            ?.map { VideoTrackSelectionManager.TrackInfo(it.groupIndex, it.trackIndex, it.label, it.isSelected) }
            ?: emptyList()

    override fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) =
        videoPlayerManagerProvider()?.selectSubtitleTrack(groupIndex, trackIndex) ?: Unit

    override fun getHueAdjustmentDegrees(): Float =
        videoPlayerManagerProvider()?.getHueAdjustmentDegrees() ?: 0f

    override fun setHueAdjustmentDegrees(degrees: Float) {
        videoPlayerManagerProvider()?.setHueAdjustmentDegrees(degrees)
    }

    override fun getBrightnessProgress(): Int =
        videoPlayerManagerProvider()?.getBrightnessProgress() ?: 50

    override fun setBrightnessProgress(progress: Int) {
        videoPlayerManagerProvider()?.setBrightnessProgress(progress)
    }

    override fun getBrightnessPercentOffset(): Int =
        videoPlayerManagerProvider()?.getBrightnessPercentOffset() ?: 0

    override fun getPlaybackSpeed(): Float =
        if (isAudioServiceActive()) {
            audioPlayerProvider()?.playbackParameters?.speed ?: 1.0f
        } else {
            videoPlayerManagerProvider()?.getPlayer()?.playbackParameters?.speed ?: 1.0f
        }

    override fun setPlaybackSpeed(speed: Float) {
        if (isAudioServiceActive()) {
            audioPlayerProvider()?.setPlaybackSpeed(speed)
        } else {
            videoPlayerManagerProvider()?.setPlaybackSpeed(speed)
        }
    }
}
