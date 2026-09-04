package com.sza.fastmediasorter.wear.ui.player.image

import com.sza.fastmediasorter.wear.domain.model.MAX_COUNTER_DISPLAY_COUNT
import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearPlaybackMode

/**
 * UI state for the image viewer screen.
 */
data class ImageViewerUiState(
    val isLoading: Boolean = true,
    val mediaFile: WearMediaFile? = null,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val error: String? = null,
    val isSlideshowActive: Boolean = false,
    // S2480: the viewer opens on the picture alone - a panel that covers the lower half of a round
    // watch on entry is what the owner reported, and a tap is what brings it back.
    val showControls: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val playbackMode: WearPlaybackMode = WearPlaybackMode.SEQUENTIAL,
    val scaleMode: VideoScaleMode = VideoScaleMode.FIT,
    val closeScreen: Boolean = false
) {
    val positionText: String
        get() = if (totalCount > 0) {
            val totalStr = if (totalCount > MAX_COUNTER_DISPLAY_COUNT) "###" else totalCount.toString()
            "${currentIndex + 1}/$totalStr"
        } else {
            ""
        }
}
