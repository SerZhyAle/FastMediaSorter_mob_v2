package com.sza.fastmediasorter.ui.launcher

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.launcher.LauncherWallpaper
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("FunctionNaming") // backtick test names, project convention
class LauncherWallpaperResolutionTest {

    @Test
    fun `static stripes mode resolves to a motionless branded frame`() {
        assertEquals(
            LauncherWallpaper.StaticStripes,
            resolveLauncherWallpaper(
                mode = AppSettings.LAUNCHER_WALLPAPER_STATIC_STRIPES,
                imagePath = "",
                imageAvailable = false,
            ),
        )
    }
}
