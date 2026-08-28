package com.sza.fastmediasorter.ui.browse.managers

import android.view.View
import com.sza.fastmediasorter.domain.model.MediaFile

/**
 * Flavor-aware decorative binder for APK tile badges in Browse.
 *
 * src/main owns only the hook; flavor source sets decide whether any badge exists.
 */
interface BrowseApkTileBadgeBinder {

    fun bind(root: View, mediaFile: MediaFile)

    fun onViewRecycled(root: View) = Unit

    /**
     * Height in pixels this variant's badge claims along the top edge of a browse tile for [mediaFile].
     *
     * Answered for every file that *could* receive a badge, not only for those that end up with one:
     * classification is asynchronous, and a band that appeared once the answer arrived would relayout the
     * tile under the user. src/main learns the number only through this hook, never the flavor's geometry.
     */
    fun reservedTopBandPx(mediaFile: MediaFile): Int = 0
}