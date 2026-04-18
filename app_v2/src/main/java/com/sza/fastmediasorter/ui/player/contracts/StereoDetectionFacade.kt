package com.sza.fastmediasorter.ui.player.contracts

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.StereoDetector
import javax.inject.Inject

/**
 * Backend-agnostic facade over the stereo mode detector.
 * Hides crop-preview and render-specific details of StereoVideoProcessor.
 * Available to all flavors through main source set.
 */
interface StereoDetectionFacade {
    /**
     * Detect stereo mode from file dimensions (aspect ratio heuristic).
     * For full detection including Matroska metadata, use [StereoDetector.detectFromFormat] directly.
     */
    fun detectFromDimensions(mediaFile: MediaFile): StereoMode

    /** Returns true if the media file is likely stereoscopic (SBS or OU). */
    fun isStereoContent(mediaFile: MediaFile): Boolean
}

class StereoDetectionFacadeImpl @Inject constructor(
    private val stereoDetector: StereoDetector
) : StereoDetectionFacade {

    override fun detectFromDimensions(mediaFile: MediaFile): StereoMode {
        val w = mediaFile.width ?: return StereoMode.UNKNOWN
        val h = mediaFile.height ?: return StereoMode.UNKNOWN
        if (w <= 0 || h <= 0) return StereoMode.UNKNOWN
        return stereoDetector.detectFromDimensions(w, h)
    }

    override fun isStereoContent(mediaFile: MediaFile): Boolean {
        val mode = detectFromDimensions(mediaFile)
        return mode == StereoMode.SBS_FULL || mode == StereoMode.SBS_HALF || mode == StereoMode.OU
    }
}
