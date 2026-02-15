package com.sza.fastmediasorter.ui.player.render

import com.sza.fastmediasorter.domain.model.MediaFile

enum class RenderPriority {
    NEXT,
    PREVIOUS,
    LOOKAHEAD
}

enum class RenderModeHint {
    FIT_CENTER,
    CENTER_CROP,
    KEEP_CURRENT
}

data class RenderTarget(
    val mediaFile: MediaFile,
    val path: String,
    val priority: RenderPriority,
    val modeHint: RenderModeHint = RenderModeHint.KEEP_CURRENT,
    val requestId: Long = System.nanoTime()
)
