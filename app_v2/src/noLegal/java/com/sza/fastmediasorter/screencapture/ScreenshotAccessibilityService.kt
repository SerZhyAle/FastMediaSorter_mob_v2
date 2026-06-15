package com.sza.fastmediasorter.screencapture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SaveScreenshotUseCase
import com.sza.fastmediasorter.util.ScreenshotDestinationPolicy
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * API 30+ dialog-free capture path for S0405. As an accessibility service it can both host the
 * edge gesture strip (via a TYPE_ACCESSIBILITY_OVERLAY window, no SYSTEM_ALERT_WINDOW) and grab the
 * current screen with takeScreenshot() - silently, with no per-capture MediaProjection consent.
 * The strip is only managed on API 30+; on API 26..29 the OverlayHostService + MediaProjection
 * fallback owns the gesture instead.
 */
@AndroidEntryPoint
class ScreenshotAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var settingsRepository: Lazy<SettingsRepository>

    @Inject
    lateinit var resourceRepository: Lazy<ResourceRepository>

    @Inject
    lateinit var saveScreenshotUseCase: Lazy<SaveScreenshotUseCase>

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var overlayManager: ScreenGestureOverlayManager? = null

    @Volatile
    private var captureInProgress = false

    private val screenshotCallback = object : TakeScreenshotCallback {
        override fun onSuccess(screenshot: ScreenshotResult) {
            processScreenshotResult(screenshot)
        }

        override fun onFailure(errorCode: Int) {
            captureInProgress = false
            Timber.w("ScreenshotAccessibilityService: takeScreenshot failed, code=%d", errorCode)
            val messageRes = if (errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                R.string.screen_capture_rate_limited
            } else {
                R.string.save_frame_error
            }
            toast(getString(messageRes), Toast.LENGTH_LONG)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScreenshotAccessibilityServiceHolder.instance = this
        serviceScope.launch {
            val enabled = settingsRepository.get().getSettings().first().gestureOverlayEnabled
            applyOverlayState(enabled)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event consumption: this service exists only to host the gesture strip and capture.
    }

    override fun onInterrupt() {
        // Nothing to interrupt - no ongoing accessibility feedback.
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        serviceScope.cancel()
        super.onDestroy()
    }

    /** Show/hide the edge gesture strip. Called on connect and pushed by the settings controller. */
    fun applyOverlayState(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (enabled) showStrip() else hideStrip()
    }

    private fun showStrip() {
        if (overlayManager != null) return
        // Accessibility (dialog-free) takes priority: if the legacy MediaProjection host was running
        // its own strip, shut it down so only one strip exists once accessibility is on.
        OverlayHostService.stop(this)
        val manager = ScreenGestureOverlayManager(
            context = this,
            overlayWindowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            onGestureMatched = { captureNow() }
        )
        manager.show()
        overlayManager = manager
    }

    private fun hideStrip() {
        overlayManager?.hide()
        overlayManager = null
    }

    private fun teardown() {
        hideStrip()
        if (ScreenshotAccessibilityServiceHolder.instance === this) {
            ScreenshotAccessibilityServiceHolder.instance = null
        }
    }

    private fun captureNow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (captureInProgress) return
        captureInProgress = true
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, screenshotCallback)
    }

    private fun processScreenshotResult(screenshot: ScreenshotResult) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            captureInProgress = false
            return
        }
        val hardwareBuffer = screenshot.hardwareBuffer
        val bitmap: Bitmap? = try {
            Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)?.let { hardwareBitmap ->
                val software = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                hardwareBitmap.recycle()
                software
            }
        } finally {
            hardwareBuffer.close()
        }

        if (bitmap == null) {
            captureInProgress = false
            Timber.e("ScreenshotAccessibilityService: failed to wrap screenshot hardware buffer")
            toast(getString(R.string.save_frame_error), Toast.LENGTH_LONG)
            return
        }

        serviceScope.launch { saveBitmap(bitmap) }
    }

    private suspend fun saveBitmap(bitmap: Bitmap) {
        try {
            val settings = settingsRepository.get().getSettings().first()
            val resources = resourceRepository.get().getAllResourcesSync()
            val target = ScreenshotDestinationPolicy().resolve(
                selectedResourceId = settings.screenshotDestinationResourceId,
                resources = resources
            )
            when (val result = saveScreenshotUseCase.get().invoke(bitmap, target)) {
                is SaveScreenshotUseCase.SaveResult.Success ->
                    toast(getString(R.string.screen_capture_saved_to, result.destinationLabel, result.fileName))
                is SaveScreenshotUseCase.SaveResult.Failure -> {
                    Timber.e(result.error, "ScreenshotAccessibilityService: screenshot save failed")
                    toast(getString(R.string.save_frame_error), Toast.LENGTH_LONG)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "ScreenshotAccessibilityService: capture processing failed")
            toast(getString(R.string.save_frame_error), Toast.LENGTH_LONG)
        } finally {
            captureInProgress = false
        }
    }

    private fun toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }
}
