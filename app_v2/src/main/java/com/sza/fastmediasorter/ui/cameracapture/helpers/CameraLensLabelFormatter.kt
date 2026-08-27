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
     * Label for [entry] within [offered], unique inside that set.
     *
     * S2120: the focal length is not a terminal answer. A Galaxy S21+ publishes one front sensor as two
     * logical cameras of the same 3.3 mm focal length, so both rows read "Front 3.3 mm" and the picker
     * cannot express which one is being chosen. A name that still repeats therefore carries the entry
     * id, which is unique by construction - it is the key the choice is persisted under, and the token
     * the System info lens report prints - so the chain can no longer end in a tie.
     */
    fun label(context: Context, entry: CameraLensEntry, offered: List<CameraLensEntry>): String {
        val named = namedBySize(context, entry, offered)
        val repeats = offered.count { namedBySize(context, it, offered) == named } > 1
        return if (repeats) "$named (${entry.id})" else named
    }

    /**
     * Type name, plus the focal length when two lenses of one facing rank identically - a device with
     * two mid-range back lenses reads as "Wide" twice. Kept separate from [label] so a richer
     * disambiguator (strategic §5.3) replaces this rule alone.
     */
    private fun namedBySize(
        context: Context,
        entry: CameraLensEntry,
        offered: List<CameraLensEntry>,
    ): String {
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
