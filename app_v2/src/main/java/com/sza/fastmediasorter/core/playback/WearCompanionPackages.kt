package com.sza.fastmediasorter.core.playback

/**
 * S2810 / S2941: identifies the Wear OS companion packages that bridge the phone's active
 * [androidx.media3.session.MediaSession] to the watch's system "now playing" surface, so
 * [com.sza.fastmediasorter.ui.player.AudioPlaybackService.AudioSessionCallback.onConnect] can
 * refuse them.
 *
 * Two families, both fixed by the Wear OS platform rather than discoverable at runtime:
 * - The Google and Samsung companion apps, matched by exact package name.
 * - The Samsung per-model plugin packages (e.g. `com.samsung.wearable.watch7plugin`), matched by
 *   [SAMSUNG_WEAR_PLUGIN_PREFIX]. Samsung ships a distinct plugin package per watch model, so an
 *   exact list cannot enumerate them; a S2925 device pass on a Galaxy Watch7 connected through
 *   `com.samsung.wearable.watch7plugin`, which the exact list missed.
 */
object WearCompanionPackages {

    const val GOOGLE_WEAR_COMPANION_PACKAGE = "com.google.android.wearable.app"
    const val SAMSUNG_WEAR_COMPANION_PACKAGE = "com.samsung.android.wearable.app"

    /** Per-model Samsung plugin packages share this prefix (e.g. `com.samsung.wearable.watch7plugin`). */
    private const val SAMSUNG_WEAR_PLUGIN_PREFIX = "com.samsung.wearable."

    private val exactPackages = setOf(
        GOOGLE_WEAR_COMPANION_PACKAGE,
        SAMSUNG_WEAR_COMPANION_PACKAGE,
    )

    /**
     * True when [packageName] is a Wear OS companion bridge: one of the exact companion apps, or a
     * Samsung per-model plugin. Null-safe because [MediaSession.ControllerInfo.getPackageName] is
     * annotated nullable on some API paths.
     */
    fun isWearCompanion(packageName: String?): Boolean =
        packageName != null &&
            (packageName in exactPackages || packageName.startsWith(SAMSUNG_WEAR_PLUGIN_PREFIX))
}
