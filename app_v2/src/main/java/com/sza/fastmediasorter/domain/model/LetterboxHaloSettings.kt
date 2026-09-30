package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.core.letterbox.LetterboxFillMath

/**
 * The LETTERBOX-HALO options behind the photo bars, nested as the contract offers them: the halo
 * needs the bars switch (`AppSettings.dynamicBackgroundExtension`), growth needs the halo, speed needs
 * growth. One nested group keeps `AppSettings` inside its constructor-slot ceiling.
 *
 * [speed] is `slow`, `medium` or `fast`; any other stored value reads as `medium` (halo rule 5).
 */
data class LetterboxHaloSettings(
    val enabled: Boolean = false,
    val growth: Boolean = true,
    val speed: String = LetterboxFillMath.SPEED_MEDIUM,
)
