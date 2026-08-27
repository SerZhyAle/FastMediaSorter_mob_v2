package com.sza.fastmediasorter.ui.launcher.helpers

import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLensEnumerationManager
import com.sza.fastmediasorter.ui.cameracapture.model.CameraLensEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * S2076: owns the desktop's live camera backdrop - the CameraX provider, the one bound use case, and the
 * unbind at the background edge.
 *
 * Binds a [Preview] and nothing else (strategic ADR-3). The capture screen's own factory builds the still
 * and video use cases beside it, which a wallpaper never uses and which would cost buffers for the whole
 * time the desktop is on screen; binding through the capture session would additionally let a zoom or
 * aspect change made while taking a photo reach the desktop backdrop.
 *
 * Permission is deliberately not checked here: the resolver upstream only ever emits the camera mode when
 * the grant and the hardware are both present, so this class is reached with the question already
 * answered (strategic ADR-2).
 */
class LauncherCameraBackgroundManager(
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
) {

    private val lensEnumeration = CameraLensEnumerationManager()
    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Binds the preview to [cameraId], or to the lens the capture screen would start on if it is gone.
     *
     * Awaits the provider instead of registering a listener on its future: a future listener has no
     * removal counterpart, so it would outlive a desktop the user already left.
     */
    fun start(cameraId: String) {
        lifecycleOwner.lifecycleScope.launch {
            runCatching {
                val context = previewView.context
                // get() blocks until CameraX is up, so it waits off the main thread; the bind itself
                // goes back to the main thread because CameraX requires it.
                val provider = withContext(Dispatchers.IO) {
                    ProcessCameraProvider.getInstance(context).get()
                }
                cameraProvider = provider
                bind(provider, cameraId)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                // The desktop keeps whatever it is already showing; a toast here would fire on a screen
                // the user opens dozens of times a day.
                Timber.e(error, "Launcher camera backdrop could not start")
            }
        }
    }

    /** Releases the camera. Symmetric with [start]; safe to call when nothing is bound. */
    fun stop() {
        runCatching { cameraProvider?.unbindAll() }
            .onFailure { Timber.e(it, "Launcher camera backdrop could not stop") }
        cameraProvider = null
    }

    private fun bind(provider: ProcessCameraProvider, cameraId: String) {
        val entry = resolveEntry(provider, cameraId) ?: run {
            Timber.w("Launcher camera backdrop: no lens available")
            return
        }
        provider.unbindAll()
        val preview = Preview.Builder()
            .also { builder ->
                // A physical sub-lens is reachable only by naming it; a logical entry leaves this alone.
                entry.physicalCameraId?.let { Camera2Interop.Extender(builder).setPhysicalCameraId(it) }
            }
            .build()
            .apply { surfaceProvider = previewView.surfaceProvider }
        provider.bindToLifecycle(lifecycleOwner, selectorFor(entry), preview)
    }

    private fun resolveEntry(provider: ProcessCameraProvider, cameraId: String): CameraLensEntry? {
        val offered = lensEnumeration.select(lensEnumeration.expand(provider))
        if (offered.isEmpty()) return null
        return offered.firstOrNull { it.id == cameraId }
            ?: offered.getOrNull(lensEnumeration.initialLensIndex(offered))
    }

    private fun selectorFor(entry: CameraLensEntry): CameraSelector =
        runCatching { entry.cameraInfo.cameraSelector }.getOrElse {
            CameraSelector.Builder().requireLensFacing(entry.lensFacing).build()
        }
}
