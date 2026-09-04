package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.sza.fastmediasorter.wear.domain.model.WearBackground

data class WearWallpaperState(
    val background: WearBackground = WearBackground.BrandedAnimation,
    val showsWallpaper: Boolean = true,
    val isResumed: Boolean = true
)

val LocalWearWallpaperState = compositionLocalOf { WearWallpaperState() }
