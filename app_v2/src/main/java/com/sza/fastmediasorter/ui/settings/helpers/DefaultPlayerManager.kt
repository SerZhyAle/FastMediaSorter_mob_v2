package com.sza.fastmediasorter.ui.settings.helpers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.ui.player.MediaButtonRestartReceiver
import timber.log.Timber

/**
 * Phase 5 + 6 + 7: Manages enabling/disabling of default player components at runtime.
 *
 * Phase 5 - Primary Player toggle ("Use as primary media player"):
 *   Controls ACTION_VIEW aliases + MediaButtonRestartReceiver.
 *   When ON: this app appears in the system file chooser and handles hardware media buttons.
 *
 * Phase 6 - Share Receiver toggle ("Accept shared media files"):
 *   Controls ACTION_SEND aliases.
 *   When ON: this app appears in the system Share sheet for media files.
 *
 * Phase 7 - Flavor-aware alias filtering:
 *   Only the aliases whose media type is supported by the current flavor are touched.
 *   e.g. the photos flavor (SUPPORT_AUDIO=false, SUPPORT_VIDEO=false) will never enable
 *   the audio/video aliases even when the primary player toggle is ON.
 */
object DefaultPlayerManager {

    /**
     * The manifest namespace used for activity-alias class names.
     *
     * Android resolves `android:name=".StandaloneAudioPlayer"` relative to the manifest
     * *namespace* ("com.sza.fastmediasorter"), not the runtime *applicationId* which may
     * carry a suffix like ".debug" or ".lite". ComponentName's class-name argument must
     * use the namespace, while its package argument must use context.packageName (applicationId)
     * so the system can locate the installed APK.
     */
    private const val NAMESPACE = "com.sza.fastmediasorter"

    /**
     * Phase 7: Returns only the ACTION_VIEW alias suffixes that are valid for this flavor.
     * Aliases for unsupported media types are intentionally left in their manifest-default
     * (disabled) state - they are never activated at runtime.
     */
    private fun viewAliasesForFlavor(caps: MediaCapabilities): List<String> = buildList {
        if (caps.supportsAudio)     add(".StandaloneAudioPlayer")
        if (caps.supportsVideo)     add(".StandaloneVideoPlayer")
        if (caps.supportsImages)    add(".StandaloneImagePlayer")
        if (caps.supportsDocuments) add(".StandaloneDocsPlayer")
        // S0380: plain text gets its own lightweight standalone activity; included unconditionally
        // (matches the unconditional text share receiver), and the typeless dispatcher trampoline that
        // forwards generic / typeless VIEW intents to whichever specialized activity actually fits.
        add(".StandaloneTextPlayer")
        add(".StandaloneTypelessPlayer")
    }

    /**
     * Phase 7: Returns only the ACTION_SEND alias suffixes that are valid for this flavor.
     * StandaloneTextSender is included unconditionally - plain text is tiny and universally useful.
     */
    private fun sendAliasesForFlavor(caps: MediaCapabilities): List<String> = buildList {
        if (caps.supportsAudio)  add(".StandaloneAudioSender")
        if (caps.supportsVideo)  add(".StandaloneVideoSender")
        if (caps.supportsImages) add(".StandaloneImageSender")
        add(".StandaloneTextSender")
    }

    /**
     * Phase 5: Enable or disable ACTION_VIEW aliases and MediaButtonRestartReceiver.
     * Only aliases for types supported by this flavor are affected.
     */
    fun applyPrimaryPlayerState(context: Context, enabled: Boolean, caps: MediaCapabilities) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val pm = context.packageManager
        val pkg = context.packageName

        viewAliasesForFlavor(caps).forEach { alias ->
            setComponentState(pm, ComponentName(pkg, "$NAMESPACE$alias"), state)
        }

        setComponentState(
            pm,
            ComponentName(context, MediaButtonRestartReceiver::class.java),
            state
        )

        Timber.d("DefaultPlayerManager: primary player ${if (enabled) "ENABLED" else "DISABLED"} (aliases: ${viewAliasesForFlavor(caps)})")
    }

    /**
     * Phase 6: Enable or disable ACTION_SEND aliases.
     * Only aliases for types supported by this flavor are affected.
     */
    fun applyShareReceiverState(context: Context, enabled: Boolean, caps: MediaCapabilities) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val pm = context.packageManager
        val pkg = context.packageName

        sendAliasesForFlavor(caps).forEach { alias ->
            setComponentState(pm, ComponentName(pkg, "$NAMESPACE$alias"), state)
        }

        Timber.d("DefaultPlayerManager: share receiver ${if (enabled) "ENABLED" else "DISABLED"} (aliases: ${sendAliasesForFlavor(caps)})")
    }

    private fun setComponentState(
        pm: PackageManager,
        component: ComponentName,
        state: Int
    ) {
        try {
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        } catch (e: Exception) {
            Timber.e(e, "DefaultPlayerManager: failed to set state for ${component.shortClassName}")
        }
    }
}
