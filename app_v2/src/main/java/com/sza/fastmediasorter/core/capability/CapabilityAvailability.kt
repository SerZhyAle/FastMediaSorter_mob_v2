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
 * VR exposure here is compile-time only - whether the immersive runtime is even present in the build.
 * The per-device "is this a headset right now" check stays in the XR detection facade and is combined
 * by the consumer. The structured OCR-reason variant (for user-facing copy) is deferred to S0400.
 */
@Singleton
class CapabilityAvailability @Inject constructor(
    @CompiledCapabilities private val compiled: Set<@JvmSuppressWildcards String>
) {

    fun isTranslationAvailable(): Boolean = CAP_TRANSLATION in compiled

    fun isVrAvailable(): Boolean = CAP_VR in compiled

    fun isOcrCompiledIn(): Boolean = CAP_OCR in compiled

    fun isOcrAvailable(context: Context): Boolean =
        isOcrCompiledIn() && DeviceCapabilities.isOcrSupported(context)

    fun isExtensionsScreenAvailable(): Boolean = isOcrCompiledIn() || isTranslationAvailable()

    /**
     * Whether the build ships more than one OCR engine and therefore exposes the engine/model
     * picker plus the noLegal-OCR language labels. Only the noLegal flavor bundles PaddleOCR
     * alongside the default Tesseract path, so this is the compile-time axis behind those rows.
     */
    fun isOcrEngineSelectionAvailable(): Boolean = CAP_OCR_ENGINE_SELECTION in compiled

    /** Whether the GPL NewPipe extractor is linked in (noLegal only) - gates its license card. */
    fun isNewPipeAvailable(): Boolean = CAP_NEWPIPE in compiled

    companion object {
        const val CAP_OCR = "ocr"
        const val CAP_TRANSLATION = "translation"
        const val CAP_VR = "vr"
        const val CAP_OCR_ENGINE_SELECTION = "ocr_engine_selection"
        const val CAP_NEWPIPE = "newpipe"
    }
}
