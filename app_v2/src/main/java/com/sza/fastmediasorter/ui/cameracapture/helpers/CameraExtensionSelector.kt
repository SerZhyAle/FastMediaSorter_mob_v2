package com.sza.fastmediasorter.ui.cameracapture.helpers

import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager

/**
 * S1262: picks the single CameraX extension that binds for the current intents.
 *
 * Exactly one extension can be bound, so the intents are ranked rather than combined: HDR and night
 * are explicit user choices and outrank a portrait profile that merely also asked for bokeh.
 *
 * It lives outside [CameraCaptureSessionManager] rather than as a method on it because that class
 * sits exactly on detekt's 40-function ceiling - a third branch in its bind path had to go somewhere,
 * and a pure resolver is the half worth having its own home anyway.
 */
object CameraExtensionSelector {

    /**
     * The three extension-backed intents, used both for what was asked and for what the lens has.
     * Declaration order is the ranking order, so a positional call site reads in priority sequence.
     */
    data class Intents(
        val hdr: Boolean = false,
        val night: Boolean = false,
        val bokeh: Boolean = false,
    )

    fun resolve(
        manager: ExtensionsManager?,
        baseSelector: CameraSelector,
        requested: Intents,
        available: Intents,
    ): CameraSelector {
        if (manager == null) return baseSelector
        val mode = when {
            requested.hdr && available.hdr -> ExtensionMode.HDR
            requested.night && available.night -> ExtensionMode.NIGHT
            requested.bokeh && available.bokeh -> ExtensionMode.BOKEH
            else -> null
        }
        return mode?.let { manager.getExtensionEnabledCameraSelector(baseSelector, it) } ?: baseSelector
    }
}
