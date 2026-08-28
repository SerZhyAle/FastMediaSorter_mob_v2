package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLensEnumerationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume

/**
 * S2210: performs a single-frame CameraX photo capture for instant photo wallpaper mode.
 * Captures one still frame to app-private storage and immediately unbinds to release the camera.
 */
class LauncherInstantPhotoCaptureManager(
    private val context: Context,
) {

    private val lensEnumeration = CameraLensEnumerationManager()

    @Suppress("TooGenericExceptionCaught")
    suspend fun captureSingleFrame(cameraId: String, lifecycleOwner: LifecycleOwner): String? =
        withContext(Dispatchers.IO) {
            try {
                Timber.d("Instant photo capture requested for camera %s", cameraId)
                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                val selector = resolveCameraSelector(cameraProvider, cameraId) ?: run {
                    Timber.w("Could not resolve camera selector for %s", cameraId)
                    return@withContext null
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                withContext(Dispatchers.Main) {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, imageCapture)
                }

                val outputFile = File(context.filesDir, INSTANT_PHOTO_FILENAME)
                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

                val success = suspendCancellableCoroutine<Boolean> { continuation ->
                    val executor = ContextCompat.getMainExecutor(context)
                    imageCapture.takePicture(
                        outputOptions,
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                Timber.d("Instant photo saved to %s", outputFile.absolutePath)
                                if (continuation.isActive) continuation.resume(true)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Timber.e(exception, "Instant photo capture failed")
                                if (continuation.isActive) continuation.resume(false)
                            }
                        },
                    )
                }

                withContext(Dispatchers.Main) {
                    cameraProvider.unbindAll()
                }

                if (success && outputFile.isFile) {
                    outputFile.absolutePath
                } else {
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Instant photo capture failed with exception")
                null
            }
        }

    private fun resolveCameraSelector(provider: ProcessCameraProvider, cameraId: String): CameraSelector? {
        val entry = lensEnumeration.expand(provider)
            .firstOrNull { it.id == cameraId } ?: return null
        return CameraSelector.Builder()
            .requireLensFacing(entry.lensFacing)
            .build()
    }

    companion object {
        private const val INSTANT_PHOTO_FILENAME = "instant_photo_wallpaper.jpg"
    }
}
