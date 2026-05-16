package com.sza.fastmediasorter.core.memory

import android.graphics.Bitmap
import com.bumptech.glide.load.DecodeFormat
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for image decode format.
 * Replaces duplicated LOW-tier branches and also feeds non-Glide bitmap decoders.
 */
@Singleton
class MemoryPressureDecodeFormatResolver @Inject constructor(
    private val coordinator: MemoryProfileCoordinator,
    private val pressureMonitor: NativePressureMonitor,
) {

    @Volatile
    private var pressureOverrideLogged: Boolean = false

    fun decodeFormat(): DecodeFormat {
        val decision = resolveDecision()
        return decodeFormatFor(decision.profile.useRgb565, decision.underPressure)
    }

    fun bitmapConfig(): Bitmap.Config {
        val decision = resolveDecision()
        return bitmapConfigFor(decision.profile.useRgb565, decision.underPressure)
    }

    private fun resolveDecision(): Decision {
        val profile = coordinator.current()
        val underPressure = pressureMonitor.isUnderPressure()

        if (underPressure && !profile.useRgb565) {
            if (!pressureOverrideLogged) {
                Timber.i(
                    "MemoryPressureDecodeFormatResolver: pressure override → RGB_565 (free=%dMB, scenario=%s)",
                    pressureMonitor.lastFreeNativeMb(),
                    profile.scenario.name,
                )
                pressureOverrideLogged = true
            }
        } else {
            pressureOverrideLogged = false
        }

        return Decision(profile = profile, underPressure = underPressure)
    }

    private data class Decision(
        val profile: MemoryProfile,
        val underPressure: Boolean,
    )

    companion object {
        internal fun shouldUseRgb565(profileUseRgb565: Boolean, underPressure: Boolean): Boolean {
            return profileUseRgb565 || underPressure
        }

        internal fun decodeFormatFor(
            profileUseRgb565: Boolean,
            underPressure: Boolean,
        ): DecodeFormat {
            return if (shouldUseRgb565(profileUseRgb565, underPressure)) {
                DecodeFormat.PREFER_RGB_565
            } else {
                DecodeFormat.PREFER_ARGB_8888
            }
        }

        internal fun bitmapConfigFor(
            profileUseRgb565: Boolean,
            underPressure: Boolean,
        ): Bitmap.Config {
            return if (shouldUseRgb565(profileUseRgb565, underPressure)) {
                Bitmap.Config.RGB_565
            } else {
                Bitmap.Config.ARGB_8888
            }
        }
    }
}