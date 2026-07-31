package com.sza.fastmediasorter.ui.cameracapture.helpers

import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import com.sza.fastmediasorter.ui.cameracapture.model.CameraLensEntry
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import java.util.Locale

/**
 * Renders the lens set the capture screen would offer, as plain text for the System info report
 * (S1261 defect D3): the platform section shows what Camera2 declares, this block shows what the
 * app derived from it - the selected entries, the one the session would start on, each entry's
 * equivalent multiplier and the zoom-preset labels it would produce.
 *
 * Pure text out, no view types: the owner's phone has ADB off, so this report is the only channel
 * that proves which lens set and presets the app actually built on that device.
 */
class CameraLensSelectionReporter {

    private val enumeration = CameraLensEnumerationManager()

    /** One line per offered lens; empty when enumeration yields nothing (defensive, never throws). */
    fun report(provider: ProcessCameraProvider): List<String> {
        val offered = enumeration.select(enumeration.expand(provider))
        if (offered.isEmpty()) return emptyList()
        // The same rule the session uses for its first bind - the report must show the truth.
        val startIndex = enumeration.initialLensIndex(offered)
        return offered.mapIndexed { index, entry ->
            lineOf(entry, index == startIndex)
        }
    }

    private fun lineOf(entry: CameraLensEntry, isStart: Boolean): String {
        val multiplier = entry.equivalentMultiplier
        // The zoom ceiling comes from the logical camera the entry binds through - a physical
        // sub-lens publishes no zoom state of its own (same fallback the enumeration uses).
        val maxZoom = runCatching { entry.cameraInfo.zoomState.value?.maxZoomRatio }.getOrNull()
            ?: CameraRuntimeCapabilities.DEFAULT_ZOOM
        val presets = CameraRuntimeCapabilities.buildZoomPresets(
            entry.minZoomRatio,
            maxZoom,
            multiplier,
            CameraRuntimeCapabilities.DIGITAL_ZOOM_CAP,
        )
        val labels = if (presets.isEmpty()) {
            "none"
        } else {
            presets.joinToString(" ") { presetLabel(it, multiplier) }
        }
        val marker = if (isStart) " <- start" else ""
        return String.format(
            Locale.US,
            "[%s] %s, focal %.2f mm, min zoom %.2f, mult %.2f, presets: %s%s",
            entry.id,
            facingText(entry.lensFacing),
            entry.focalLengthMm,
            entry.minZoomRatio,
            multiplier,
            labels,
            marker,
        )
    }

    /** The label a zoom pill would print for [preset]: display-rounded equivalent (S1260 rule). */
    private fun presetLabel(preset: Float, multiplier: Float): String {
        val display = CameraRuntimeCapabilities.roundEquivalentForDisplay(preset * multiplier)
        val wholeNumber = display == display.toInt().toFloat()
        return if (wholeNumber) {
            String.format(Locale.US, "%dx", display.toInt())
        } else {
            String.format(Locale.US, "%.1fx", display)
        }
    }

    private fun facingText(facing: Int): String = when (facing) {
        CameraSelector.LENS_FACING_BACK -> "back"
        CameraSelector.LENS_FACING_FRONT -> "front"
        else -> "external"
    }
}
