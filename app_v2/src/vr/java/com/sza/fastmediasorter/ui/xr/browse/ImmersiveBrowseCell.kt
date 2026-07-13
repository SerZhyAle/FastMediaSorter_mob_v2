package com.sza.fastmediasorter.ui.xr.browse

import android.graphics.Bitmap
import android.graphics.RectF
import com.sza.fastmediasorter.core.xr.VrMediaType

/**
 * One tile in the immersive browse grid. Pure data holder - the grid renderer owns layout and
 * paints, the decoder owns [thumbnail]. [bounds] and [thumbnail] are mutable because they are
 * filled in-place per frame/page to avoid reallocating a cell list on every layout pass.
 */
data class ImmersiveBrowseCell(
    val index: Int,
    val label: String,
    val isFolder: Boolean,
    val mediaType: VrMediaType,
    // Non-null only when isFolder; the folder this tile descends into.
    val folderPath: String?,
    // Non-null only when !isFolder; absolute path or content-uri string of the media file.
    val filePath: String?,
    // Short stereo/projection tag ("SBS"/"OU"/"360") or null for plain 2D media.
    val stereoBadge: String?,
    // Assigned by the grid renderer during layout(); empty until the cell enters the page window.
    var bounds: RectF = RectF(),
    // Null until the decoder fills it; a null thumbnail draws a placeholder instead.
    var thumbnail: Bitmap? = null,
)
