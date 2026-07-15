package com.sza.fastmediasorter.core.util

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Queries whether the platform can decode animated images (animated WebP, APNG) into an
 * `AnimatedImageDrawable` via `ImageDecoder.decodeDrawable`.
 *
 * `ImageDecoder` / `AnimatedImageDrawable` exist since API 28 (Android 9). Below that no viable
 * pure-Java animated-WebP/APNG path exists, so those files keep the static first-frame behavior
 * from Glide's default decoders (unchanged).
 */
object AnimatedImageSupportUtils {

    /** Returns true if this device can decode animated WebP/APNG to an animated drawable (API 28+). */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    fun isAnimatedImageDecodeSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
}
