package com.sza.fastmediasorter.data.detector

import android.content.Context
import android.content.res.Configuration
import com.sza.fastmediasorter.data.model.DetectionConfidence
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.detector.DeviceProfileDetector
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber

/**
 * Real implementation of device profile detector.
 * Analyzes Android platform signals (features, UI mode, screen size)
 * to recommend a device profile with confidence level.
 *
 * Detection order (priority):
 * 1. HIGH confidence: XR/VR, Automotive, TV/Leanback
 * 2. MEDIUM confidence: Screen width (tablet vs phone)
 * 3. LOW confidence: Fallback to safe default (PERSONAL_SMARTPHONE)
 */
class RealDeviceProfileDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceProfileDetector {

    override suspend fun detectProfile(): DeviceProfileDetector.DetectionResult {
        val signals = mutableListOf<String>()
        val uiMode = DetectionHelper.getUiModeType(context)

        // HIGH confidence - explicit device class. UiModeManager is the strongest signal Android
        // exposes; PackageManager features are the backstop for OEMs that don't set the UI mode.
        if (uiMode == Configuration.UI_MODE_TYPE_VR_HEADSET || DetectionHelper.hasVrFeatures(context)) {
            signals.add(if (uiMode == Configuration.UI_MODE_TYPE_VR_HEADSET) "ui_mode_vr_headset" else "has_vr_features")
            if (DetectionHelper.isKnownVrHeadsetManufacturer()) signals.add("known_vr_manufacturer")
            return result(DeviceProfileType.VR_HEADSET, DetectionConfidence.HIGH, signals)
        }
        if (uiMode == Configuration.UI_MODE_TYPE_CAR || DetectionHelper.hasAutomotiveFeature(context)) {
            signals.add(if (uiMode == Configuration.UI_MODE_TYPE_CAR) "ui_mode_car" else "has_automotive_feature")
            return result(DeviceProfileType.CAR_HEAD_UNIT, DetectionConfidence.HIGH, signals)
        }
        if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION || DetectionHelper.hasTelevisionFeature(context)) {
            signals.add(if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) "ui_mode_television" else "has_television_feature")
            return result(DeviceProfileType.TV_MEDIA_BOX, DetectionConfidence.HIGH, signals)
        }

        // MEDIUM confidence - desktop / Chromebook / Samsung DeX and large screens map to the
        // "Tablet & desktop mode" profile (HOME_TABLET); §5.2 treats desktop as a tablet modifier.
        val smallestWidthDp = DetectionHelper.getSmallestWidthDp(context)
        if (DetectionHelper.isChromebook(context) || DetectionHelper.hasPcFeature(context) ||
            uiMode == Configuration.UI_MODE_TYPE_DESK
        ) {
            signals.add("desktop_or_chromebook")
            return result(DeviceProfileType.HOME_TABLET, DetectionConfidence.MEDIUM, signals)
        }
        if (smallestWidthDp >= 600) {
            signals.add("smallest_width_${smallestWidthDp}dp")
            return result(DeviceProfileType.HOME_TABLET, DetectionConfidence.MEDIUM, signals)
        }
        if (smallestWidthDp in 1 until 600) {
            signals.add("smallest_width_${smallestWidthDp}dp")
            if (DetectionHelper.hasTelephonyFeature(context)) signals.add("has_telephony")
            return result(DeviceProfileType.PERSONAL_SMARTPHONE, DetectionConfidence.MEDIUM, signals)
        }

        // LOW confidence - no reliable signal; safe handheld fallback.
        signals.add("fallback_unknown")
        Timber.i("Device profile detector: low confidence, falling back to PERSONAL_SMARTPHONE; signals=$signals")
        return result(DeviceProfileType.PERSONAL_SMARTPHONE, DetectionConfidence.LOW, signals)
    }

    private fun result(
        profile: DeviceProfileType,
        confidence: DetectionConfidence,
        signals: List<String>,
    ) = DeviceProfileDetector.DetectionResult(profile, confidence, signals)

    override fun getSignalForProfile(type: DeviceProfileType): DeviceProfileDetector.Signal? {
        return when (type) {
            DeviceProfileType.VR_HEADSET -> DeviceProfileDetector.Signal("has_vr_features", true)
            DeviceProfileType.CAR_HEAD_UNIT -> DeviceProfileDetector.Signal("has_automotive_feature", true)
            DeviceProfileType.TV_MEDIA_BOX -> DeviceProfileDetector.Signal("has_television_feature", true)
            DeviceProfileType.HOME_TABLET -> DeviceProfileDetector.Signal("smallest_width_dp", ">= 600")
            DeviceProfileType.PERSONAL_SMARTPHONE -> DeviceProfileDetector.Signal("smallest_width_dp", "< 600")
            else -> null
        }
    }
}
