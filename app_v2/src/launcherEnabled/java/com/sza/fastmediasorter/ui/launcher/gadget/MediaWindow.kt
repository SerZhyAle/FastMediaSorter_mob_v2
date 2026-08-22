package com.sza.fastmediasorter.ui.launcher.gadget

import android.net.Uri
import com.sza.fastmediasorter.domain.model.MediaFile

/**
 * S1754: what the four media windows share - the numbers and the one rule each of them would otherwise
 * repeat.
 *
 * Its own file rather than a companion inside one of the four: a constant that three siblings read from
 * a fourth is a constant nobody finds.
 */
internal object MediaWindow {

    /** Every media window seeds as a square: a strip cannot show a frame, a document or a transport row. */
    const val SPAN = 2

    /** Read wide, then filter by type: the loader applies its limit before this gadget sees a type. */
    const val SCAN_LIMIT = 60

    /** Slide interval of the image window. Slow on purpose - this is a desktop, not a viewer. */
    const val TICK_MS = 6_000L

    /** Floor for a Glide override while the cell has not been measured yet. */
    const val MIN_DECODE_PX = 240

    /**
     * Only device-local sources are decoded: Glide and ExoPlayer are handed the typed models
     * `AdapterThumbnailLoader` builds, never a bare SMB/cloud path, so a network file would fail
     * silently. Same rule [FolderPreviewGadget] applies, for the same reason.
     */
    fun localModel(file: MediaFile): Uri? = when {
        file.contentUri?.startsWith("content://") == true -> Uri.parse(file.contentUri)
        file.path.startsWith("/") -> Uri.parse("file://${file.path}")
        else -> null
    }
}
