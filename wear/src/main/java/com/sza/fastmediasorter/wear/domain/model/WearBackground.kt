package com.sza.fastmediasorter.wear.domain.model

import java.io.File

/**
 * What the watch actually draws behind its screens (S2000).
 *
 * Distinct from [WearBackgroundMode], which is only what the owner chose on the phone. A chosen mode
 * of [WearBackgroundMode.IMAGE] still resolves to [BrandedAnimation] whenever the frame is missing or
 * unreadable, so screens ask for this rather than for the mode.
 */
sealed interface WearBackground {

    data object BrandedAnimation : WearBackground

    data object BrandedStill : WearBackground

    data class Image(val file: File, val lastModified: Long = file.lastModified()) : WearBackground

    data object None : WearBackground
}
