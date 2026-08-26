package com.sza.fastmediasorter.domain.delivery

/**
 * Heavy artifact groups delivered on demand (S0386 strategic §5.4).
 *
 * - [TRANSLATION]: ML Kit Translate + language-id (Set A).
 * - [OCR_ENGINES]: Tesseract native engine (Set B).
 * - [AUDIO_VISUALIZATIONS]: audio-player background videos (Set C).
 * - [FFMPEG_DTS]: FFmpeg DTS decoder (Set D).
 * - [CHANNEL_PREVIEW_ATLAS]: on-demand stream channel-preview sprite sheet + url->index sidecar (S1154).
 * - [STREAM_LOGO_ATLAS]: on-demand stream logo sprite sheet + url->index sidecar (S1201). Covers what
 *   the preview atlas structurally cannot - a station with no video track has no frame to capture.
 * - [VLC_ENGINE]: libVLC decoder (Set E, S1971). noLegal + arm64-v8a only, and the only set that is
 *   never bundled anywhere - its 44 MB is what the ticket removes from the sideload artifact.
 */
enum class DeliverableSet {
    TRANSLATION,
    OCR_ENGINES,
    AUDIO_VISUALIZATIONS,
    FFMPEG_DTS,
    CHANNEL_PREVIEW_ATLAS,
    STREAM_LOGO_ATLAS,
    VLC_ENGINE
}
