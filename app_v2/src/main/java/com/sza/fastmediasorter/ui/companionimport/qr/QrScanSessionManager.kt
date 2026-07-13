package com.sza.fastmediasorter.ui.companionimport.qr

import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * S0988: minimal CameraX scan session on [LifecycleCameraController]. The controller owns the
 * provider and lifecycle wiring internally, so the scan screen only supplies a [PreviewView], an
 * analyzer and a lifecycle - no manual provider future to juggle. Its default analysis output is
 * YUV_420_888, which is exactly what [QrCodeAnalyzer] reads. Single owner: [unbind] detaches the
 * controller and shuts the analysis executor down.
 */
class QrScanSessionManager(
    private val lifecycleOwner: LifecycleOwner
) {

    private var controller: LifecycleCameraController? = null
    private var analysisExecutor: ExecutorService? = null

    fun bind(previewView: PreviewView, onDecoded: (String) -> Unit) {
        val executor = Executors.newSingleThreadExecutor().also { analysisExecutor = it }
        val cam = LifecycleCameraController(previewView.context.applicationContext).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            setImageAnalysisAnalyzer(executor, QrCodeAnalyzer(onDecoded))
            bindToLifecycle(lifecycleOwner)
        }
        controller = cam
        previewView.controller = cam
    }

    fun setTorch(enabled: Boolean) {
        controller?.enableTorch(enabled)
    }

    fun unbind() {
        controller?.unbind()
        controller = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
    }
}
