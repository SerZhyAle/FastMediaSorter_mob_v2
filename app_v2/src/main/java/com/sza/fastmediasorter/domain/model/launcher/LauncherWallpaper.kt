package com.sza.fastmediasorter.domain.model.launcher

/**
 * S1101: what the launcher desktop draws behind its cells.
 *
 * Resolved from the settings token plus the stored copy path, so the render layer never has to
 * re-check whether a path is present for a given mode: an [Image] instance is proof of both.
 */
sealed interface LauncherWallpaper {

    /** Flat theme surface - the pre-S1101 look. */
    data object None : LauncherWallpaper

    /** Branded procedural waves-and-particles animation, the default. */
    data object Branded : LauncherWallpaper

    /** One fresh, motionless branded frame, replaced only when the launcher returns to the foreground. */
    data object StaticStripes : LauncherWallpaper

    /** User image (still or GIF) copied into app-private storage. */
    data class Image(val absolutePath: String) : LauncherWallpaper

    /**
     * S2076: live frame from a device camera.
     *
     * [cameraId] is the `CameraLensEntry.id` form - the logical camera id, optionally followed by `/` and a
     * physical sub-lens id. An instance is proof the camera was reachable when the mode was resolved, the
     * same way an [Image] instance is proof of a present path.
     */
    data class LiveCamera(val cameraId: String) : LauncherWallpaper

    /**
     * S2210: one still frame captured from a device camera each time the launcher returns to the
     * foreground, with the camera released again as soon as the frame lands.
     *
     * [imagePath] is null until the first capture of the session succeeds, which degrades to the branded
     * backdrop the same way a missing [Image] copy does. A non-null path is proof the capture landed, the
     * same way an [Image] instance is proof of a present path, so the render layer never stats the file -
     * it draws on the main thread, where a stat is a StrictMode violation.
     *
     * Every capture overwrites one app-private file, so the path alone repeats and cannot signal a new
     * frame - [capturedAtMillis] carries that identity, and doubles as the image cache key. Without it the
     * render layer would keep showing the previous capture forever.
     */
    data class InstantPhoto(
        val cameraId: String,
        val imagePath: String? = null,
        val capturedAtMillis: Long = 0L,
    ) : LauncherWallpaper
}
