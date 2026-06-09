package com.sza.fastmediasorter.domain.delivery

/**
 * Heavy artifact groups delivered on demand (S0386 strategic §5.4).
 *
 * - [TRANSLATION]: ML Kit Translate + language-id (Set A).
 * - [OCR_ENGINES]: Tesseract + PaddleOCR native engines (Set B).
 * - [AUDIO_VISUALIZATIONS]: audio-player background videos (Set C).
 * - [FFMPEG_DTS]: FFmpeg DTS decoder (Set D).
 */
enum class DeliverableSet {
    TRANSLATION,
    OCR_ENGINES,
    AUDIO_VISUALIZATIONS,
    FFMPEG_DTS
}
