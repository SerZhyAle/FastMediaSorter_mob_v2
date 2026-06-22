package com.sza.fastmediasorter.ui.settings.search

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.sza.fastmediasorter.core.util.DeviceCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-row device-feature filter for the settings-search index. Some rows live in an
 * always-available section (so [SettingsSearchAvailability] cannot drop them) and survive the
 * flavor/DI [SettingsSearchCapabilityGate], yet are hidden at runtime when the device lacks a
 * hardware/OS capability the owning fragment checks. Indexing them anyway yields dead search
 * results: search returns the row, tapping it lands on a screen where it is GONE.
 *
 * This is the device axis, complementary to [SettingsSearchCapabilityGate] (flavor/DI/compile
 * axis). Both are ANDed in [SettingsSearchRegistry], so a row gated on both axes (the camera-OCR
 * rows) must pass each. Each branch mirrors a runtime predicate so search visibility cannot drift
 * from UI visibility:
 *  - `rowEnablePip` - `PlaybackSettingsFragment` shows `layoutPip` only on API 31+ (PiP support).
 *  - `rowFollowSystemRotation` / `rowFollowSystemRotationPlayer` - `OperationsSettingsFragment` /
 *    `PlaybackSettingsFragment` show the rotation rows only when an accelerometer is present.
 *  - `rowCameraOcrTranslationEnabled` / `rowCameraOcrOnly` - `OperationsSettingsFragment` shows the
 *    camera-OCR rows only when [DeviceCapabilities.isOcrSupported] (RAM + API floor for ML Kit).
 *  - `spinnerOcrEngineType` / `spinnerPaddleOcrModel` / `spinnerOcrFontSize` / `spinnerOcrFontFamily`
 *    (S0603) - `OtherMediaSettingsFragment` force-disables the OCR toggle when OCR is unsupported,
 *    so these in-app OCR pickers can never be revealed on such a device.
 *
 * No `BuildConfig` is read (Rule 15): the signals are pure device/OS facts. Transient action
 * buttons whose absence is dominated by runtime permission state (the notification-permission
 * buttons) are de-indexed at the source (S0604) rather than gated here - their dead-ness is mutable
 * runtime state, not a device-feature axis.
 */
@Singleton
class SettingsSearchDeviceFeatureGate @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val supportsPictureInPicture: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private val hasAccelerometer: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
    }

    private val supportsOcr: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        DeviceCapabilities.isOcrSupported(context)
    }

    fun isAvailable(key: String): Boolean =
        decide(key, supportsPictureInPicture, hasAccelerometer, supportsOcr)

    internal companion object {
        /** Pure decision table - extracted so the key->signal mapping is unit-testable without a Context. */
        @VisibleForTesting
        internal fun decide(
            key: String,
            supportsPictureInPicture: Boolean,
            hasAccelerometer: Boolean,
            supportsOcr: Boolean
        ): Boolean = when (key) {
            "rowEnablePip" -> supportsPictureInPicture
            "rowFollowSystemRotation", "rowFollowSystemRotationPlayer" -> hasAccelerometer
            // Camera-OCR rows + the in-app OCR pickers (S0603): on a device that fails
            // DeviceCapabilities.isOcrSupported the OCR toggle is force-disabled, so these spinners
            // can never be revealed - GONE in UI, dead in search. Mirror the device gate.
            "rowCameraOcrTranslationEnabled", "rowCameraOcrOnly",
            "spinnerOcrEngineType", "spinnerPaddleOcrModel",
            "spinnerOcrFontSize", "spinnerOcrFontFamily" -> supportsOcr
            else -> true
        }
    }
}
