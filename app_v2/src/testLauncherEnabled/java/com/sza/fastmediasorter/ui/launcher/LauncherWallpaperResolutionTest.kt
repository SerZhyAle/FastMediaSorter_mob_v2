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

    @Test
    fun `instant photo mode carries the frame this session captured`() {
        assertEquals(
            LauncherWallpaper.InstantPhoto(CAMERA_ID, CAPTURE_PATH, CAPTURED_AT),
            resolveLauncherWallpaper(
                mode = AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO,
                imagePath = "",
                imageAvailable = false,
                cameraId = CAMERA_ID,
                cameraAvailable = true,
                instantPhoto = InstantPhotoFrame(CAPTURE_PATH, CAPTURED_AT),
            ),
        )
    }

    @Test
    fun `instant photo mode holds no frame until the first capture lands`() {
        assertEquals(
            LauncherWallpaper.InstantPhoto(CAMERA_ID, null, 0L),
            resolveLauncherWallpaper(
                mode = AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO,
                imagePath = "",
                imageAvailable = false,
                cameraId = CAMERA_ID,
                cameraAvailable = true,
                instantPhoto = null,
            ),
        )
    }

    /**
     * S2210: the stored path is the wallpaper image the user picked themselves. Reading it here would put
     * their own picture on screen as though the camera had just taken it, and the capture that followed
     * would overwrite the pick.
     */
    @Test
    fun `instant photo mode ignores the stored user image path`() {
        assertEquals(
            LauncherWallpaper.InstantPhoto(CAMERA_ID, null, 0L),
            resolveLauncherWallpaper(
                mode = AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO,
                imagePath = "/data/user/0/app/files/user_picked_wallpaper.png",
                imageAvailable = true,
                cameraId = CAMERA_ID,
                cameraAvailable = true,
                instantPhoto = null,
            ),
        )
    }

    @Test
    fun `instant photo mode degrades to the branded backdrop when the camera is unavailable`() {
        assertEquals(
            LauncherWallpaper.Branded,
            resolveLauncherWallpaper(
                mode = AppSettings.LAUNCHER_WALLPAPER_INSTANT_PHOTO,
                imagePath = "",
                imageAvailable = false,
                cameraId = CAMERA_ID,
                cameraAvailable = false,
                instantPhoto = InstantPhotoFrame(CAPTURE_PATH, CAPTURED_AT),
            ),
        )
    }

    private companion object {
        // The CameraLensEntry.id form: a logical camera plus a physical sub-lens.
        const val CAMERA_ID = "0/2"
        const val CAPTURE_PATH = "/data/user/0/app/files/instant_photo_wallpaper.jpg"
        const val CAPTURED_AT = 1_700_000_000_000L
    }
}
