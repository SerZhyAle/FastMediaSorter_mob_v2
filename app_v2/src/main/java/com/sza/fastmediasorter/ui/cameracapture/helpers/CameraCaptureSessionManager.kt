package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.annotation.SuppressLint
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber
import java.io.File

class CameraCaptureSessionManager(
    private val lifecycleOwner: LifecycleOwner,
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    @SuppressLint("MissingPermission")
    fun bind(
        previewView: PreviewView,
        onReady: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        val context = previewView.context
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                runCatching {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder().build()

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                    )

                    cameraProvider = provider
                    imageCapture = capture
                    onReady()
                }.onFailure { error ->
                    Timber.e(error, "CameraCaptureSessionManager: bind failed")
                    onError(error)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun capture(
        previewView: PreviewView,
        outputFile: File,
        onSaved: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError(IllegalStateException("Camera is not bound"))
            return
        }

        capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(previewView.context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onSaved()
                }

                override fun onError(exception: ImageCaptureException) {
                    Timber.e(exception, "CameraCaptureSessionManager: capture failed")
                    onError(exception)
                }
            },
        )
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
    }
}
