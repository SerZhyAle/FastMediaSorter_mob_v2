package com.sza.fastmediasorter.ui.player

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.StereoMode

/**
 * S1618: session-scoped playback preferences applied to ExoPlayer.
 *
 * These outlived the dialog that used to edit them - the playback-control dialog owns every one of
 * these choices now. So this is applied state rather than a screen's model: it resets with the
 * player session and is never persisted.
 */
data class PlayerSettings(
    val playbackSpeed: Float = 1.0f,
    val repeatVideo: Boolean = false,
    val showSubtitles: Boolean = false,
    val subtitleLanguage: LanguageOption = LanguageOption.DEFAULT,
    val audioLanguage: LanguageOption = LanguageOption.DEFAULT,
    /** AUTO leaves the decision to StereoDetector, which reads metadata and aspect ratio. */
    val stereoMode: StereoMode = StereoMode.AUTO,
) {

    /** Track language a preference can ask for; `StreamTrackLanguage` names the same set for streams. */
    enum class LanguageOption(val code: String, val displayNameResId: Int) {
        DEFAULT("default", R.string.language_default),
        ENGLISH("en", R.string.language_english),
        RUSSIAN("ru", R.string.language_russian),
        UKRAINIAN("uk", R.string.language_ukrainian),
    }
}
