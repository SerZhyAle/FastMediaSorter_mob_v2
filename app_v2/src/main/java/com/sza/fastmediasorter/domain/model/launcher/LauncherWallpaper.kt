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
}
