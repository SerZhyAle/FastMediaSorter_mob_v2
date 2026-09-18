package com.sza.fastmediasorter.core.capability

import android.content.Context
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.util.DeviceCapabilities
import com.sza.fastmediasorter.core.util.LicensedDeviceClass
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
 * source-set modules; the device-runtime axis is folded in via [DeviceCapabilities] for OCR (RAM/API)
 * and via [LicensedDeviceClass] for translation, where it encodes a licence restriction rather than a
 * hardware one - the ML Kit Translation terms permit only phones, tablets, laptops and desktops.
 *
 * VR exposure here is compile-time only - whether the immersive runtime is even present in the build.
 * The per-device "is this a headset right now" check stays in the XR detection facade and is combined
 * by the consumer. The structured OCR-reason variant (for user-facing copy) is deferred to S0400.
 */
@Singleton
class CapabilityAvailability @Inject constructor(
    @CompiledCapabilities private val compiled: Set<@JvmSuppressWildcards String>
) {

    /** Whether ML Kit translation is linked into this build - the compile axis alone. */
    fun isTranslationCompiledIn(): Boolean = CAP_TRANSLATION in compiled

    /**
     * Whether translation may be offered here: linked into the build AND running on a device class
     * the ML Kit Translation licence permits (S1625).
     *
     * There is deliberately no no-argument overload. The device axis was added to a predicate a dozen
     * call sites already answered on the compile axis alone, and an overload would have left every one
     * of them compiling unchanged and still violating the terms.
     */
    fun isTranslationAvailable(context: Context): Boolean =
        translationSupport(context) is TranslationSupport.Supported

    /**
     * Same decision as [isTranslationAvailable] with the reason attached. A surface that must explain
     * itself needs to tell "this build never shipped translation" from "this device class is not
     * licensed for it" - those two states owe the user different copy.
     */
    fun translationSupport(context: Context): TranslationSupport {
        val deviceClass = LicensedDeviceClass.current(context)
        return when {
            !isTranslationCompiledIn() -> TranslationSupport.Unsupported(
                reason = TranslationUnavailableReason.NOT_COMPILED_IN,
                deviceClass = deviceClass,
            )
            deviceClass !in LicensedDeviceClass.MLKIT_TRANSLATION_ALLOWED -> TranslationSupport.Unsupported(
                reason = TranslationUnavailableReason.DEVICE_CLASS_NOT_LICENSED,
                deviceClass = deviceClass,
            )
            else -> TranslationSupport.Supported
        }
    }

    fun isVrAvailable(): Boolean = CAP_VR in compiled

    /** Whether this build links a cloud account at all - the compile axis behind every cloud surface. */
    fun isCloudAvailable(): Boolean = CAP_CLOUD in compiled

    fun isOcrCompiledIn(): Boolean = CAP_OCR in compiled

    fun isOcrAvailable(context: Context): Boolean =
        isOcrCompiledIn() && DeviceCapabilities.isOcrSupported(context)

    /** Whether the Streams feature surface is offered in this build (compile-time capability flag). */
    fun isStreamsAvailable(): Boolean = BuildConfig.SUPPORT_STREAMS

    /**
     * Whether persistent (background) audio playback is compiled into this build. The flag is
     * declared in every flavor block, so reading it here is variant-safe, and this is the only
     * place shared code may read it (CLAUDE.md Rule 14).
     *
     * S1379: the previous wording called this the single source of truth behind the settings gate
     * while `PlaybackSettingsFragment` was still reading the flag itself - the claim came first and
     * the callers followed later. Every consumer now asks here; the one remaining direct reader is
     * the permission registry, which maps a flag NAME arriving as a string and is not a consumer
     * guard at all.
     */
    fun isPersistentAudioPlaybackAvailable(): Boolean = BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK

    fun isExtensionsScreenAvailable(context: Context): Boolean =
        isOcrCompiledIn() || isTranslationAvailable(context) || isStreamsAvailable()

    /** Whether the GPL NewPipe extractor is linked in (noLegal only) - gates its license card. */
    fun isNewPipeAvailable(): Boolean = CAP_NEWPIPE in compiled

    enum class TranslationUnavailableReason {
        /** This flavor does not link ML Kit translation at all. */
        NOT_COMPILED_IN,

        /** The ML Kit Translation licence does not permit this device class (S1625). */
        DEVICE_CLASS_NOT_LICENSED,
    }

    sealed interface TranslationSupport {
        object Supported : TranslationSupport
        data class Unsupported(
            val reason: TranslationUnavailableReason,
            val deviceClass: LicensedDeviceClass.DeviceClass,
        ) : TranslationSupport
    }

    companion object {
        const val CAP_OCR = "ocr"
        const val CAP_TRANSLATION = "translation"
        const val CAP_VR = "vr"
        const val CAP_NEWPIPE = "newpipe"
        const val CAP_CLOUD = "cloud"
    }
}
