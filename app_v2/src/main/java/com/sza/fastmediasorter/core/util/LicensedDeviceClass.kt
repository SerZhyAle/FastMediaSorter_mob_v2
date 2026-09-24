package com.sza.fastmediasorter.core.util

import android.content.Context
import android.content.res.Configuration
import com.sza.fastmediasorter.data.detector.DetectionHelper
import timber.log.Timber

/**
 * Device class as a LICENCE decides it, not as the UI presents it.
 *
 * Sits next to [DeviceCapabilities] and answers a neighbouring question. [DeviceCapabilities] asks
 * whether the hardware can run an optional feature at all; this object asks whether the terms of a
 * third-party library permit running it here, which is a legal fact about the device class and not a
 * capability of the device.
 *
 * Deliberately NOT built on `RealDeviceProfileDetector`: that classifier answers "which UX preset
 * suits this device", defaults ambiguous signals to a smartphone (permissive), and its result is
 * user-overridable and persisted. A licence gate must default the other way and must not be editable
 * by the person it restricts.
 */
object LicensedDeviceClass {

    enum class DeviceClass {
        HANDHELD,
        TABLET,
        DESKTOP,
        CHROMEBOOK,
        TELEVISION,
        AUTOMOTIVE,
        XR_HEADSET,
        WATCH,
        UNKNOWN,
    }

    /**
     * Device classes on which the ML Kit on-device Translation API may be used.
     *
     * Transcribed from https://developers.google.com/ml-kit/language/translation/translation-terms:
     * the API "may not be used in any applications for any embedded devices such as cars, TVs,
     * appliances, or speakers without Google's prior written permission", and the permitted list is
     * smartphones, tablets, laptops and desktop computers.
     *
     * The set is an ALLOW list on purpose. A deny list would stay silent about the next device class
     * Android grows and would therefore violate the terms by default. Adding a member here is not a
     * code decision - it requires Google's prior written permission.
     */
    val MLKIT_TRANSLATION_ALLOWED: Set<DeviceClass> = setOf(
        DeviceClass.HANDHELD,
        DeviceClass.TABLET,
        DeviceClass.DESKTOP,
        DeviceClass.CHROMEBOOK,
    )

    /** smallestScreenWidthDp at or above which a normal-UI-mode device is a tablet, not a handheld. */
    private const val TABLET_MIN_SMALLEST_WIDTH_DP = 600

    @Volatile
    private var cached: DeviceClass? = null

    private val cacheLock = Any()

    /**
     * Device class of the machine this process runs on, resolved once and reused. The inputs are
     * system features and the UI mode, none of which change under a running process, and the answer
     * is read from view-visibility paths that recompute on every settings emission.
     */
    fun current(context: Context): DeviceClass {
        cached?.let { return it }
        return synchronized(cacheLock) {
            cached ?: classify(context).also {
                cached = it
                Timber.i("LicensedDeviceClass: resolved device class %s", it.name)
            }
        }
    }

    /** Whether the ML Kit on-device Translation API is licensed for this device class. */
    fun isMlKitTranslationAllowed(context: Context): Boolean = current(context) in MLKIT_TRANSLATION_ALLOWED

    /**
     * Restrictions are tested before permissions: a device reporting several signals at once - a
     * headset that also reports a normal UI mode - must be judged by the restriction it matches.
     */
    private fun classify(context: Context): DeviceClass {
        val uiMode = DetectionHelper.getUiModeType(context)
        return when {
            isXrHeadset(context, uiMode) -> DeviceClass.XR_HEADSET
            DetectionHelper.hasAutomotiveFeature(context) ||
                uiMode == Configuration.UI_MODE_TYPE_CAR -> DeviceClass.AUTOMOTIVE
            DetectionHelper.hasTelevisionFeature(context) ||
                uiMode == Configuration.UI_MODE_TYPE_TELEVISION -> DeviceClass.TELEVISION
            uiMode == Configuration.UI_MODE_TYPE_WATCH -> DeviceClass.WATCH
            DetectionHelper.isChromebook(context) -> DeviceClass.CHROMEBOOK
            DetectionHelper.hasPcFeature(context) -> DeviceClass.DESKTOP
            // Desk mode means a phone or tablet sitting in a dock, not a desktop computer.
            uiMode == Configuration.UI_MODE_TYPE_NORMAL ||
                uiMode == Configuration.UI_MODE_TYPE_DESK -> handheldOrTablet(context)
            else -> DeviceClass.UNKNOWN
        }
    }

    private fun isXrHeadset(context: Context, uiMode: Int): Boolean =
        DetectionHelper.hasVrFeatures(context) ||
            uiMode == Configuration.UI_MODE_TYPE_VR_HEADSET ||
            DetectionHelper.isKnownVrHeadsetManufacturer()

    private fun handheldOrTablet(context: Context): DeviceClass =
        if (DetectionHelper.getSmallestWidthDp(context) >= TABLET_MIN_SMALLEST_WIDTH_DP) {
            DeviceClass.TABLET
        } else {
            DeviceClass.HANDHELD
        }

    /** Drops the per-process cache so a test can classify a second stubbed device. */
    internal fun resetForTest() {
        synchronized(cacheLock) { cached = null }
    }
}
