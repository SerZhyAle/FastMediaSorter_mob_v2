# S0162 Phase 02 — ScreenRotationManager (new class)

## File

`app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ScreenRotationManager.kt`

---

## Responsibility

Single point of truth for `activity.requestedOrientation` in the player.
All other code that sets `requestedOrientation` in player context must route through this class
or call `reapply()` after its own transient lock (see ADR-4: Draw Mode interaction).

---

## Class skeleton

```kotlin
package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.pm.ActivityInfo
import android.provider.Settings
import timber.log.Timber

/**
 * Manages screen orientation for the player.
 *
 * Two-level hierarchy (S0162 §5.3):
 *  - followSystem=true  → delegate to OS auto-rotate; player trigger hidden
 *  - followSystem=false → own control; sensorEnabled drives requestedOrientation
 *
 * All callers must use [apply] or [reapply]; never set [Activity.requestedOrientation]
 * directly in player code (except transient locks in Draw Mode — but those must call
 * [reapply] on exit).
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
     *  - Settings observer (when followSystem changes)
     *  - Player toggle callback (when sensorEnabled changes)
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
            followSystem -> {
                // Delegate to OS: read ACCELEROMETER_ROTATION system setting
                val osAutoRotate = try {
                    Settings.System.getInt(
                        activity.contentResolver,
                        Settings.System.ACCELEROMETER_ROTATION
                    ) == 1
                } catch (e: Settings.SettingNotFoundException) {
                    true // assume enabled if unreadable
                }
                if (osAutoRotate) ActivityInfo.SCREEN_ORIENTATION_SENSOR
                else ActivityInfo.SCREEN_ORIENTATION_LOCKED
            }
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
     * Called by [ImageDrawOverlayManager] after exiting Draw Mode to restore S0162 state.
     * Requires [apply] to have been called at least once for the cached state to be valid.
     */
    fun reapply(activity: Activity, hasAccelerometer: Boolean) {
        apply(activity, currentFollowSystem, currentSensorEnabled, hasAccelerometer)
    }
}
```

---

## Notes

- Not a Hilt singleton — instantiated once per PlayerActivity lifecycle via `by lazy {}`.
- `reapply()` is the hook for `ImageDrawOverlayManager.stopDraw()` (Phase 05, ADR-4).
- `Settings.System.ACCELEROMETER_ROTATION` reading requires no special permission (public setting).
- The `SCREEN_ORIENTATION_LOCKED` constant locks to the current physical orientation at the
  moment of the call — not necessarily portrait. Correct for "freeze in current position."

---

## Acceptance

- `apply(followSystem=true, osAutoRotate=ON)` → `SCREEN_ORIENTATION_SENSOR`
- `apply(followSystem=true, osAutoRotate=OFF)` → `SCREEN_ORIENTATION_LOCKED`
- `apply(followSystem=false, sensorEnabled=true)` → `SCREEN_ORIENTATION_SENSOR`
- `apply(followSystem=false, sensorEnabled=false)` → `SCREEN_ORIENTATION_LOCKED`
- `apply(hasAccelerometer=false, …)` → no-op, no crash
- `reapply()` reproduces the last `apply()` result without additional parameters
