package com.sza.fastmediasorter.core.xr

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContract.SynchronousResult

/**
 * Shared `ActivityResultContract` between UI callers (badge, overflow menu, settings test
 * button, future browse entries) and the immersive XR host Activity.
 *
 * - [createIntent] delegates to [XrEntryGateway.createImmersiveIntent] so the gateway stays
 *   the single source of truth for "where does immersive playback launch."
 * - [parseResult] collapses the Android [Activity.RESULT_OK] / [Activity.RESULT_CANCELED]
 *   code plus the returning [VrLaunchInput.EXTRA_LAUNCH_RESULT] payload into a non-throwing
 *   [VrLaunchResult].
 *
 * If [XrEntryGateway.createImmersiveIntent] returns `null` (capability lost between request
 * and launch, missing file URI, etc.), [getSynchronousResult] returns the matching typed
 * unavailable result before Android tries to dispatch an Activity.
 */
class VrPlaybackActivityContract(
    private val entryGateway: XrEntryGateway,
    private val payloadHolder: VrLaunchPayloadHolder,
) : ActivityResultContract<VrLaunchInput, VrLaunchResult>() {

    override fun getSynchronousResult(
        context: Context,
        input: VrLaunchInput,
    ): SynchronousResult<VrLaunchResult>? {
        return if (entryGateway.createImmersiveIntent(input) == null) {
            SynchronousResult(resolveUnavailableResult(input))
        } else {
            null
        }
    }

    override fun createIntent(context: Context, input: VrLaunchInput): Intent {
        return entryGateway.createImmersiveIntent(input)
            ?: Intent(ACTION_UNAVAILABLE_FALLBACK).apply {
                // No launch-input extra here: parseResult only reads EXTRA_LAUNCH_RESULT, and the
                // launch input never travels back through this synthetic fallback (S0382).
                putExtra(VrLaunchInput.EXTRA_LAUNCH_RESULT_TOKEN, payloadHolder.put(resolveUnavailableResult(input)))
            }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): VrLaunchResult {
        val payload = payloadHolder.consume<VrLaunchResult>(
            intent?.getStringExtra(VrLaunchInput.EXTRA_LAUNCH_RESULT_TOKEN)
        )
        // When createIntent emitted the synthetic fallback, Android still routes back through
        // parseResult with RESULT_CANCELED and our fallback action. Preserve the exact typed
        // unavailable result instead of collapsing transport failures into "user cancelled."
        if (intent?.action == ACTION_UNAVAILABLE_FALLBACK) {
            return payload ?: VrLaunchResult.Unavailable(VrLaunchUnavailableReason.NoRuntime)
        }
        if (payload != null) return payload
        return when (resultCode) {
            Activity.RESULT_OK -> VrLaunchResult.CompletedNormally
            Activity.RESULT_CANCELED -> VrLaunchResult.CancelledByUser
            else -> VrLaunchResult.Crashed("unexpected_result_code=$resultCode")
        }
    }

    private fun resolveUnavailableResult(input: VrLaunchInput): VrLaunchResult.Unavailable {
        val reason = if (
            input.launchMode == VrLaunchMode.FILE_URI &&
            input.fileUriString.isNullOrBlank()
        ) {
            VrLaunchUnavailableReason.InvalidUri
        } else {
            VrLaunchUnavailableReason.NoRuntime
        }
        return VrLaunchResult.Unavailable(reason)
    }

    private companion object {
        const val ACTION_UNAVAILABLE_FALLBACK = "com.sza.fastmediasorter.action.VR_UNAVAILABLE_FALLBACK"
    }
}
