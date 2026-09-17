package com.sza.fastmediasorter.ui.broadcast.helpers

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.broadcast.BroadcastLensOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * S3174: the camera picture on the broadcast screen while the user is still choosing the mode and the
 * lens, before the broadcast exists.
 *
 * The live picture belongs to `VideoBroadcastService`, which does not run yet at that point, so the
 * pre-start preview is a second, screen-owned camera session rather than a visibility change. It binds
 * nothing but [Preview]: no capture pipeline is wanted here, and every extra use case is another thing
 * the hand-over to the service has to release.
 *
 * That hand-over is why [stop] takes a callback instead of returning: CameraX closes the device
 * asynchronously after `unbindAll()`, and `RtspServerCamera2` opening the same camera in the same frame
 * fails with `CameraOpenException`. The caller starts the broadcast from the callback, not from the tap.
 *
 * Owned by [BroadcastControlManager] rather than by Hilt: it takes no dependencies, so a binding would
 * buy nothing.
 */
class BroadcastPreStreamPreviewManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var cameraProvider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var boundCameraInfo: CameraInfo? = null
    private var boundLensId: String? = null

    /** True while a preview session is bound; the screen uses it to avoid restarting an identical one. */
    val isRunning: Boolean
        get() = previewView != null

    /**
     * Binds a preview-only session for [lensId] into [container]. A session already bound to that lens is
     * left alone, so a re-render of the pre-stream block does not flicker the picture.
     */
    fun start(activity: AppCompatActivity, container: ViewGroup, lensId: String?) {
        if (isRunning && boundLensId == lensId) return
        unbindNow()
        boundLensId = lensId
        activity.lifecycleScope.launch {
            runCatching {
                // The provider future blocks on first use, so it is awaited off the main thread.
                val provider = withContext(Dispatchers.IO) {
                    ProcessCameraProvider.getInstance(activity).get()
                }
                cameraProvider = provider
                bind(activity, container, provider, lensId)
            }.onFailure { error ->
                Timber.w(error, "Broadcast pre-stream preview: no camera provider, the area stays empty")
            }
        }
    }

    /**
     * Releases the camera and reports back once the device is really closed, or once [CLOSE_TIMEOUT_MS]
     * passed without a close - the broadcast is worth attempting even on a device that never reports it.
     */
    fun stop(onClosed: () -> Unit = {}) {
        val info = boundCameraInfo
        boundCameraInfo = null
        unbindNow()
        if (info == null) {
            onClosed()
            return
        }
        scope.launch {
            val closed = withTimeoutOrNull(CLOSE_TIMEOUT_MS) {
                info.cameraState.asFlow().first { it.type == CameraState.Type.CLOSED }
            }
            if (closed == null) {
                Timber.w("Broadcast pre-stream preview: camera did not report CLOSED, starting anyway")
            }
            onClosed()
        }
    }

    @SuppressLint("MissingPermission")
    private fun bind(
        activity: AppCompatActivity,
        container: ViewGroup,
        provider: ProcessCameraProvider,
        lensId: String?,
    ) {
        val view = PreviewView(container.context)
        container.addView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        val preview = Preview.Builder().build()
        preview.surfaceProvider = view.surfaceProvider
        runCatching {
            val camera = provider.bindToLifecycle(activity, selectorFor(provider, lensId), preview)
            previewView = view
            boundCameraInfo = camera.cameraInfo
            Timber.d("S3174: pre-start preview bound to camera %s", cameraIdOf(camera.cameraInfo))
        }.onFailure { error ->
            container.removeView(view)
            Timber.w(error, "Broadcast pre-stream preview: bind failed, the area stays empty")
        }
    }

    /**
     * A sub-lens id resolves to its logical camera alone: CameraX binds logical cameras, and the physical
     * narrowing the service performs through `openPhysicalCamera` has no preview-only equivalent.
     */
    private fun selectorFor(provider: ProcessCameraProvider, lensId: String?): CameraSelector {
        val logicalId = lensId?.let(BroadcastLensOption::logicalIdOf)
        val match = logicalId?.let { wanted ->
            provider.availableCameraInfos.firstOrNull { cameraIdOf(it) == wanted }
        } ?: return CameraSelector.DEFAULT_BACK_CAMERA
        return CameraSelector.Builder()
            .addCameraFilter { infos -> infos.filter { it === match } }
            .build()
    }

    private fun cameraIdOf(info: CameraInfo): String? =
        runCatching { Camera2CameraInfo.from(info).cameraId }.getOrNull()

    private fun unbindNow() {
        runCatching { cameraProvider?.unbindAll() }
            .onFailure { Timber.w(it, "Broadcast pre-stream preview: unbind failed") }
        previewView?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
        previewView = null
        boundLensId = null
    }

    private companion object {
        /**
         * Long enough for an ordinary CameraX close, short enough that a tap on "Start broadcast" never
         * looks ignored. Chosen by the spec author (S3174), not measured against a device requirement.
         */
        const val CLOSE_TIMEOUT_MS = 1500L
    }
}
