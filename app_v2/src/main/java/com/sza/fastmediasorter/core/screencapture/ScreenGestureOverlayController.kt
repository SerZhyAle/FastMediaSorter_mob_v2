package com.sza.fastmediasorter.core.screencapture

import android.content.Context
import android.content.Intent

interface ScreenGestureOverlayController {
    /** Whether the OS permission backing the gesture overlay is granted. The concrete permission
     * (draw-over-apps vs accessibility service) is an implementation/API-level detail of the flavor. */
    fun isOverlayPermissionGranted(context: Context): Boolean

    /** Intent that takes the user to the system screen where the backing permission is granted.
     * Kept on the flavor side so `src/main` stays agnostic of which permission is required. */
    fun permissionSettingsIntent(context: Context): Intent

    /** Rationale string shown before routing the user to the permission screen. Resolved by the
     * flavor so the wording matches the actual permission requested for the current API level. */
    fun permissionRationaleResId(): Int

    /** True when a second, fallback capture method exists alongside the primary one (e.g. a
     * per-shot-consent path when the dialog-free one is unavailable). Drives whether the onboarding
     * dialog offers an "old method" choice. False when there is only one possible path. */
    fun isFallbackCaptureAvailable(): Boolean

    /** Settings intent for the fallback capture method's permission (used by the "old method" choice).
     * Only meaningful when [isFallbackCaptureAvailable] is true. */
    fun fallbackPermissionSettingsIntent(context: Context): Intent

    /** [stripVisible] is the persisted strip-visibility setting, supplied by the caller (read off the
     * Main thread) so the controller performs no settings IO on the UI thread (S0727). */
    fun setEnabled(enabled: Boolean, stripVisible: Boolean)

    /** S0724: push the left-edge strip's visibility (grey vs transparent) to the live overlay. No-op
     * when the gesture overlay is disabled. [overlayEnabled] is the persisted master toggle, supplied
     * by the caller (read off the Main thread) so this performs no settings IO on the UI thread (S0727). */
    fun setStripVisible(visible: Boolean, overlayEnabled: Boolean)
}
