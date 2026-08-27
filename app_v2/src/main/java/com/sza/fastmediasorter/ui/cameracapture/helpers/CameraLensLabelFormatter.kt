package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.content.Context
import androidx.camera.core.CameraSelector
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.cameracapture.model.CameraLensEntry
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import java.util.Locale

/**
 * S2076: names a lens for a picker that has no live session behind it.
 *
 * The capture screen names its active lens from [CameraRuntimeCapabilities], which only exists once a
 * camera is bound and a zoom state has been read. A settings picker has neither, so it decides from the
 * [CameraLensEntry] alone - but from the same five string keys, so both surfaces call the same lens the
 * same thing in every locale.
 */
class CameraLensLabelFormatter {

    private val capabilityProbe = CameraCapabilityProbe()

    /**
     * Label for [entry] within [offered]. Two lenses of one facing can rank identically - a device with
     * two mid-range back lenses reads as "Wide" twice - so a label that is not unique in the set carries
     * the focal length, which is the only thing that then tells the rows apart.
     */
    fun label(context: Context, entry: CameraLensEntry, offered: List<CameraLensEntry>): String {
        val base = context.getString(baseNameOf(entry, offered))
        val ambiguous = offered.count { baseNameOf(it, offered) == baseNameOf(entry, offered) } > 1
        if (!ambiguous || entry.focalLengthMm <= 0f) return base
        return String.format(Locale.getDefault(), "%s %.1f mm", base, entry.focalLengthMm)
    }

    private fun baseNameOf(entry: CameraLensEntry, offered: List<CameraLensEntry>): Int = when {
        entry.lensFacing == CameraSelector.LENS_FACING_FRONT -> R.string.camera_lens_front
        capabilityProbe.isDedicatedMacroLens(offered, entry) -> R.string.camera_lens_macro
        entry.equivalentMultiplier < CameraRuntimeCapabilities.DEFAULT_ZOOM -> R.string.camera_lens_ultrawide
        entry.equivalentMultiplier > CameraRuntimeCapabilities.TELE_MIN_MULTIPLIER -> R.string.camera_lens_tele
        else -> R.string.camera_lens_wide
    }
}
