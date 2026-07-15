package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.VideoTrackSelectionManager
import com.sza.fastmediasorter.ui.player.contracts.VideoPlayerHandle

/**
 * S0380: [VideoPlayerHandle] for the trimmed image/gif/video standalone surface. Pure delegation
 * over [StandaloneViewManager] (colour/speed) and the per-open [VideoTrackSelectionManager]
 * (tracks). Extracted from the Activity to keep it free of dialog-capability plumbing.
 */
class PhotoVideoStandaloneVideoHandle(
    private val viewManager: StandaloneViewManager,
    private val trackSelectionManager: () -> VideoTrackSelectionManager?,
    private val currentMediaType: () -> MediaType?,
) : VideoPlayerHandle {

    override fun getAvailableAudioTracks(): List<VideoTrackSelectionManager.TrackInfo> =
        trackSelectionManager()?.getAvailableAudioTracks() ?: emptyList()

    override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        trackSelectionManager()?.selectAudioTrack(groupIndex, trackIndex)
    }

    override fun getAvailableSubtitleTracks(): List<VideoTrackSelectionManager.TrackInfo> =
        trackSelectionManager()?.getAvailableSubtitleTracks() ?: emptyList()

    override fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) {
        trackSelectionManager()?.selectSubtitleTrack(groupIndex, trackIndex)
    }

    override fun getHueAdjustmentDegrees(): Float = viewManager.getHueAdjustmentDegrees()
    override fun setHueAdjustmentDegrees(degrees: Float) =
        viewManager.setHueAdjustmentDegrees(degrees)

    override fun getBrightnessProgress(): Int = viewManager.getBrightnessProgress()
    override fun setBrightnessProgress(progress: Int) =
        viewManager.setBrightnessProgress(progress)

    override fun getBrightnessPercentOffset(): Int = viewManager.getBrightnessPercentOffset()

    override fun setContentRotationDegrees(degrees: Int) =
        viewManager.setContentRotationDegrees(degrees)

    override fun getContentRotationDegrees(): Int = viewManager.getContentRotationDegrees()

    override fun getPlaybackSpeed(): Float = viewManager.getPlaybackSpeed(currentMediaType())

    override fun setPlaybackSpeed(speed: Float) =
        viewManager.setPlaybackSpeed(currentMediaType(), speed)
}
