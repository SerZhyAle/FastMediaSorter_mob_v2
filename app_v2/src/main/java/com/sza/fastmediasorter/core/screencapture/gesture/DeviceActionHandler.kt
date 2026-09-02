package com.sza.fastmediasorter.core.screencapture.gesture

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.Settings
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1038: runs the device-control edge-gesture actions (DEVICE picker group) that need no camera
 * preview - the flashlight toggle (via [CameraManager.setTorchMode]) and the system-brightness presets
 * (via [Settings.System], gated on `WRITE_SETTINGS`). Singleton so the best-effort torch state survives
 * across gestures. [handle] returns false for actions it does not own so the dispatcher can try the
 * next handler.
 * S2386: expands status bar panels (notification shade / quick settings) via StatusBarManager reflection.
 */
@Singleton
class DeviceActionHandler @Inject constructor() {

    // Best-effort torch state. The camera framework has no torch-state getter, and a TorchCallback would
    // be an unbalanced process-lifetime listener, so a gesture toggle tracks state locally. An external
    // torch change can desync this by one tap, which self-corrects on the next flashlight gesture.
    @Volatile
    private var torchEnabled = false

    /** Returns true when [action] is a device-control action this handler owns (performed or degraded). */
    fun handle(context: Context, action: ScreenshotGestureAction): Boolean {
        return when (action) {
            ScreenshotGestureAction.TOGGLE_FLASHLIGHT -> toggleFlashlight(context)
            ScreenshotGestureAction.BRIGHTNESS_MAX -> {
                setBrightness(context, BRIGHTNESS_MAX_VALUE)
                true
            }
            ScreenshotGestureAction.BRIGHTNESS_NORMAL -> {
                setBrightness(context, BRIGHTNESS_NORMAL_VALUE)
                true
            }
            ScreenshotGestureAction.OPEN_NOTIFICATION_SHADE -> expandNotificationShade(context)
            ScreenshotGestureAction.OPEN_QUICK_SETTINGS -> expandQuickSettings(context)
            else -> false
        }
    }

    /** Toggles the physical camera flash and reports whether this device could accept the operation. */
    fun toggleFlashlight(context: Context): Boolean {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val flashCameraId = cameraManager?.let { manager ->
            runCatching {
                manager.cameraIdList.firstOrNull { id ->
                    manager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            }.getOrNull()
        }
        return when {
            cameraManager == null -> {
                Timber.w("DeviceActionHandler: CameraManager unavailable, flashlight ignored")
                false
            }
            flashCameraId == null -> {
            Timber.w("DeviceActionHandler: no flash unit on this device, flashlight ignored")
                false
            }
            else -> runCatching {
            val next = !torchEnabled
            cameraManager.setTorchMode(flashCameraId, next)
            torchEnabled = next
            true
            }.onFailure { Timber.w(it, "DeviceActionHandler: setTorchMode failed") }.getOrDefault(false)
        }
    }

    private fun setBrightness(context: Context, value: Int) {
        if (!Settings.System.canWrite(context)) {
            // The WRITE_SETTINGS grant flow lives in the settings UI (S1038 phase 06); without the grant
            // the action stays inactive rather than failing silently into a system exception.
            Timber.w("DeviceActionHandler: WRITE_SETTINGS not granted, brightness action skipped")
            return
        }
        runCatching {
            // A written value only takes effect in manual mode; auto-brightness would override it at once.
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        }.onFailure { Timber.w(it, "DeviceActionHandler: failed to set brightness") }
    }

    private fun expandNotificationShade(context: Context): Boolean {
        return expandStatusBarPanel(context, "expandNotificationsPanel")
    }

    private fun expandQuickSettings(context: Context): Boolean {
        return expandStatusBarPanel(context, "expandSettingsPanel")
    }

    @SuppressLint("WrongConstant")
    private fun expandStatusBarPanel(context: Context, methodName: String): Boolean {
        return runCatching {
            val statusBarService = context.getSystemService("statusbar")
            if (statusBarService == null) {
                Timber.w("DeviceActionHandler: statusbar service unavailable")
                return false
            }
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            val method = statusBarManagerClass.getMethod(methodName)
            method.invoke(statusBarService)
            true
        }.onFailure {
            Timber.w(it, "DeviceActionHandler: %s failed", methodName)
        }.getOrDefault(false)
    }

    private companion object {
        // Settings.System.SCREEN_BRIGHTNESS is a 0..255 scale; max is full, normal is a mid-range default.
        private const val BRIGHTNESS_MAX_VALUE = 255
        private const val BRIGHTNESS_NORMAL_VALUE = 128
    }
}
