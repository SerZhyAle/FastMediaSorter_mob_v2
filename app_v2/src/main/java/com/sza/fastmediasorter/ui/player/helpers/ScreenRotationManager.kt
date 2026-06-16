package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import timber.log.Timber

/**
 * Manages screen orientation for the player.
 *
 * Two-level hierarchy (S0162 §5.3):
 *  - followSystem=true  → SCREEN_ORIENTATION_UNSPECIFIED: full OS delegation; no manual
 *                         ACCELEROMETER_ROTATION read. The OS applies its own auto-rotate
 *                         preference directly - works correctly on all devices including
 *                         Samsung One UI (ADR-5: reading ACCELEROMETER_ROTATION was
 *                         unreliable on some OEMs and did not react to runtime changes).
 *  - followSystem=false → own control; sensorEnabled drives requestedOrientation
 *
 * All callers must use [apply] or [reapply]; never set [Activity.requestedOrientation]
 * directly in player code (except transient locks in Draw Mode - those must call
 * [reapply] on exit per ADR-4).
 */
class ScreenRotationManager {

    // Last-known state; initialised before first [apply] call from PlayerActivity.onCreate
    private var currentFollowSystem: Boolean = true
    private var currentSensorEnabled: Boolean = true

    /**
     * Returns true if the device has a physical accelerometer.
     * When false, the entire rotation-control UI must be hidden.
     */
    fun isAccelerometerPresent(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)

    /**
     * Apply orientation based on the two-level hierarchy.
     * Call from:
     *  - PlayerActivity.onCreate / onResume
     *  - Settings observer (when the program/player follow-OS flags change)
     *  - Player toggle callback (when playerRotationSensorEnabled changes)
     */
    fun apply(
        activity: Activity,
        followSystem: Boolean,
        sensorEnabled: Boolean,
        hasAccelerometer: Boolean
    ) {
        currentFollowSystem = followSystem
        currentSensorEnabled = sensorEnabled

        if (!hasAccelerometer) {
            // No sensor → leave manifest default untouched; no UI shown
            Timber.d("ScreenRotationManager: no accelerometer, skip orientation change")
            return
        }

        val orientation = when {
            // ADR-5: use UNSPECIFIED instead of reading ACCELEROMETER_ROTATION.
            // UNSPECIFIED hands full control to the OS, which correctly honours the
            // system auto-rotate setting on all devices (including Samsung One UI where
            // ACCELEROMETER_ROTATION may stay 1 even when rotation is locked in Quick
            // Settings). Also reacts to runtime OS setting changes without an onResume.
            followSystem -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            sensorEnabled -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }

        Timber.d(
            "ScreenRotationManager: apply followSystem=$followSystem " +
                "sensorEnabled=$sensorEnabled → orientation=$orientation"
        )
        activity.requestedOrientation = orientation
    }

    /**
     * Re-apply the last-known state to [activity].
     * Called by [ImageDrawOverlayManager] after exiting Draw Mode to restore S0162 state
     * instead of the previous unconditional SCREEN_ORIENTATION_UNSPECIFIED (ADR-4).
     * Requires [apply] to have been called at least once for the cached state to be valid.
     */
    fun reapply(activity: Activity, hasAccelerometer: Boolean) {
        apply(activity, currentFollowSystem, currentSensorEnabled, hasAccelerometer)
    }
}
