package com.sza.fastmediasorter.core.compat

import android.content.Context

/**
 * Recognises a desktop-class Android device: a Googlebook (PC form factor) or a Chromebook.
 *
 * A Googlebook is not ARC++: it reports `android.hardware.type.pc` and none of the
 * `org.chromium.arc*` features, so [ChromeOsCompat] answers false there. [ChromeOsCompat]
 * stays the only ARC++ detection site; this probe only adds the PC signal next to it.
 */
object DesktopPlatformProbe {

    // String literal on purpose: PackageManager.FEATURE_PC is API 27 and minSdk is 26.
    private const val FEATURE_PC_FORM_FACTOR = "android.hardware.type.pc"

    @Volatile private var cachedIsPc: Boolean? = null

    fun isPcFormFactor(context: Context): Boolean {
        cachedIsPc?.let { return it }
        val result = runCatching {
            context.packageManager.hasSystemFeature(FEATURE_PC_FORM_FACTOR)
        }.getOrDefault(false)
        cachedIsPc = result
        return result
    }

    fun isDesktopPlatform(context: Context): Boolean =
        isPcFormFactor(context) || ChromeOsCompat.isChromeOs(context)

    internal fun resetForTest() {
        cachedIsPc = null
    }
}
