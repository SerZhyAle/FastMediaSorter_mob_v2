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
                cameraId = "",
                cameraAvailable = false,
            ),
        )
    }

    @Test
    fun `camera mode resolves to the stored lens when the camera is available`() {
        assertEquals(
            LauncherWallpaper.LiveCamera(CAMERA_ID),
            resolveLauncherWallpaper(
                mode = AppSettings.LAUNCHER_WALLPAPER_CAMERA,
                imagePath = "",
                imageAvailable = false,
                cameraId = CAMERA_ID,
                cameraAvailable = true,
            ),
        )
    }

    @Test
    fun `camera mode degrades to the branded backdrop when the camera is unavailable`() {
        assertEquals(
            LauncherWallpaper.Branded,
            resolveLauncherWallpaper(
                mode = AppSettings.LAUNCHER_WALLPAPER_CAMERA,
                imagePath = "",
                imageAvailable = false,
                cameraId = CAMERA_ID,
                cameraAvailable = false,
            ),
        )
    }

    @Test
    fun `camera mode degrades to the branded backdrop when no lens was stored`() {
        assertEquals(
            LauncherWallpaper.Branded,
            resolveLauncherWallpaper(
                mode = AppSettings.LAUNCHER_WALLPAPER_CAMERA,
                imagePath = "",
                imageAvailable = false,
                cameraId = "",
                cameraAvailable = true,
            ),
        )
    }

    private companion object {
        // The CameraLensEntry.id form: a logical camera plus a physical sub-lens.
        const val CAMERA_ID = "0/2"
    }
}
