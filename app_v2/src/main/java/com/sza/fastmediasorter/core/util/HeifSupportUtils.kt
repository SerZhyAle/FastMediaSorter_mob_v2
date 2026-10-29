package com.sza.fastmediasorter.core.util

import android.os.Build

/**
 * Utility for querying native HEIF/HEIC and AVIF decode support by Android API level.
 *
 * - HEIC/HEIF: natively decodable on API 28+ (Android 9) via ImageDecoder / BitmapFactory.
 * - AVIF: natively decodable on API 31+ (Android 12).
 *
 * On older APIs no viable pure-Java fallback exists, so these formats should show a
 * "format not supported on this device" placeholder rather than a broken-image icon.
 */
object HeifSupportUtils {

    /** Returns true if the device can natively decode HEIC/HEIF images (API 28+). */
    fun isHeicSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /** Returns true if the device can natively decode AVIF images (API 31+). */
    fun isAvifSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Returns true if the device can natively decode JXL images (not supported natively in Android framework). */
    fun isJxlSupported(): Boolean = NATIVE_JXL_DECODER

    // No framework decoder exists on any API level yet; the day one ships this becomes an SDK check like the two above.
    private const val NATIVE_JXL_DECODER = false

    /**
     * Returns true if the given file extension can be decoded on this device.
     * Standard formats (jpg, png, etc.) always return true.
     */
    fun isSupported(extension: String): Boolean = when (extension.lowercase()) {
        "heic", "heif" -> isHeicSupported()
        "avif" -> isAvifSupported()
        "jxl" -> isJxlSupported()
        else -> true
    }

    /**
     * Returns a human-readable minimum Android version string for a format, or null
     * if the format is universally supported.
     */
    fun minimumAndroidVersion(extension: String): String? = when (extension.lowercase()) {
        "heic", "heif" -> "Android 9 (API 28)"
        "avif" -> "Android 12 (API 31)"
        "jxl" -> "Android native decode unsupported"
        else -> null
    }
}
