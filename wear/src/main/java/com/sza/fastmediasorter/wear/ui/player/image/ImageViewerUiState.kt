package com.sza.fastmediasorter.wear.ui.player.image

import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile

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
    val showControls: Boolean = true,
    val isShuffleEnabled: Boolean = false,
    val scaleMode: VideoScaleMode = VideoScaleMode.FIT
) {
    val positionText: String
        get() = if (totalCount > 0) "${currentIndex + 1}/$totalCount" else ""
}
