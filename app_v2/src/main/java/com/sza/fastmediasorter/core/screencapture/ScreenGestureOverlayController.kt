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

    /** Starts/stops the gesture overlay host. S1008: the enabled + strip-visible zone sets are resolved by
     * the host off the persisted settings, so no settings IO happens on the UI thread here (S0727). */
    fun setEnabled(enabled: Boolean)

    /** S1008: refresh the live per-zone strip colours after a strip-visibility setting changed. No-op when
     * the gesture overlay is disabled. [overlayEnabled] is the persisted master toggle, supplied by the
     * caller (read off the Main thread) so this performs no settings IO on the UI thread (S0727). */
    fun setStripVisible(overlayEnabled: Boolean)
}
