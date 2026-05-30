package com.sza.fastmediasorter.ui.xr

import android.content.Intent
import com.sza.fastmediasorter.core.xr.VrLaunchDeliveryMode
import com.sza.fastmediasorter.core.xr.VrLaunchInput
import com.sza.fastmediasorter.core.xr.VrLaunchMode
import com.sza.fastmediasorter.core.xr.VrLaunchResult
import com.sza.fastmediasorter.core.xr.VrLaunchUnavailableReason
import com.sza.fastmediasorter.core.xr.VrMediaType
import com.sza.fastmediasorter.core.xr.VrPanelReturnTarget
import com.sza.fastmediasorter.core.xr.readSerializableExtraCompat
import com.sza.fastmediasorter.core.xr.PlayerStateSnapshot

/**
 * Immutable parsed view of the immersive launch transport. Extracted from the inline
 * onCreate parsing block in [DiagnosticXrActivity] so the host body stays focused on the
 * OpenXR session lifecycle rather than transport plumbing.
 *
 * - [Parsed] carries a validated [VrLaunchInput] + [VrPanelReturnTarget] pair that the host
 *   can drive without re-reading the intent.
 * - [PreflightFailure] is returned when the intent payload cannot be honoured (FILE_URI with
 *   no URI, or any other transport-level malformation). The host should hand the embedded
 *   [VrLaunchResult.Unavailable] back through its delivery path without starting an OpenXR
 *   session.
 */
internal sealed interface DiagnosticXrLaunchArgs {

    data class Parsed(
        val input: VrLaunchInput,
        val returnTarget: VrPanelReturnTarget,
    ) : DiagnosticXrLaunchArgs {

        val launchMode: VrLaunchMode get() = input.launchMode
        val mediaType: VrMediaType get() = input.mediaType
        val deliveryMode: VrLaunchDeliveryMode get() = input.deliveryMode
        val snapshot: PlayerStateSnapshot? get() = input.snapshot
    }

    data class PreflightFailure(val unavailable: VrLaunchResult.Unavailable) : DiagnosticXrLaunchArgs

    companion object {

        /**
         * Default fallback when the intent carries no [VrLaunchInput.EXTRA_LAUNCH_INPUT]
         * payload at all - keeps the legacy `Test Immersive` button working unchanged.
         */
        private val DEFAULT_INPUT = VrLaunchInput(
            launchMode = VrLaunchMode.DIAGNOSTIC_PLAYLIST,
            mediaType = VrMediaType.IMAGE,
            deliveryMode = VrLaunchDeliveryMode.LEGACY_PANEL_RETURN,
        )

        fun parse(intent: Intent, defaultReturnTarget: VrPanelReturnTarget): DiagnosticXrLaunchArgs {
            val input = intent.readSerializableExtraCompat<VrLaunchInput>(VrLaunchInput.EXTRA_LAUNCH_INPUT)
                ?: DEFAULT_INPUT
            val returnTarget = intent.readSerializableExtraCompat<VrPanelReturnTarget>(VrLaunchInput.EXTRA_RETURN_TARGET)
                ?: defaultReturnTarget

            // FILE_URI requires a non-blank URI; reject early so the host never tries to decode
            // a phantom path.
            if (input.launchMode == VrLaunchMode.FILE_URI && input.fileUriString.isNullOrBlank()) {
                return PreflightFailure(
                    VrLaunchResult.Unavailable(VrLaunchUnavailableReason.InvalidUri)
                )
            }
            return Parsed(input = input, returnTarget = returnTarget)
        }
    }
}
