package com.sza.fastmediasorter.core.capability

import android.content.Context
import com.sza.fastmediasorter.core.util.DeviceCapabilities
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt qualifier for the multibound set of capability ids compiled into the current build.
 * Each id is contributed (`@IntoSet`) by the capability source set that ships the feature:
 * `ocrEnabled` → [CapabilityAvailability.CAP_OCR], `translationEnabled` → [CapabilityAvailability.CAP_TRANSLATION],
 * `vrOnly` → [CapabilityAvailability.CAP_VR]. Flavors that mount none of these (lite/photos for
 * translation, ocrDisabled, vrStub) resolve an empty set via the `@Multibinds` default.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CompiledCapabilities

/**
 * Single source of truth for "is this optional capability available in this build, on this device".
 *
 * Onboarding pages and settings both ask this contract instead of reading build flags directly
 * (CLAUDE.md Rule 15). The compile-time axis is the multibound [compiled] set fed by per-capability
 * source-set modules; the device-runtime axis (OCR RAM/API) is folded in via [DeviceCapabilities].
 *
 * VR exposure here is compile-time only — whether the immersive runtime is even present in the build.
 * The per-device "is this a headset right now" check stays in the XR detection facade and is combined
 * by the consumer. The structured OCR-reason variant (for user-facing copy) is deferred to S0400.
 */
@Singleton
class CapabilityAvailability @Inject constructor(
    @CompiledCapabilities private val compiled: Set<@JvmSuppressWildcards String>
) {

    fun isTranslationAvailable(): Boolean = CAP_TRANSLATION in compiled

    /**
     * True when translation is delivered as a Play dynamic feature (SplitInstall) rather than bundled
     * in the APK. Dynamic-feature flavors (standard/vr) need a Play install to fetch the split;
     * bundled flavors (noLegal/legacy) work offline/sideloaded. S0400 hides the welcome translation
     * toggle on a non-Play install only when delivery is via dynamic feature.
     */
    fun isTranslationViaDynamicFeature(): Boolean = CAP_TRANSLATION_DFM in compiled

    fun isVrAvailable(): Boolean = CAP_VR in compiled

    fun isOcrCompiledIn(): Boolean = CAP_OCR in compiled

    fun isOcrAvailable(context: Context): Boolean =
        isOcrCompiledIn() && DeviceCapabilities.isOcrSupported(context)

    fun isExtensionsScreenAvailable(): Boolean = isOcrCompiledIn() || isTranslationAvailable()

    companion object {
        const val CAP_OCR = "ocr"
        const val CAP_TRANSLATION = "translation"
        const val CAP_TRANSLATION_DFM = "translation_dfm"
        const val CAP_VR = "vr"
    }
}
