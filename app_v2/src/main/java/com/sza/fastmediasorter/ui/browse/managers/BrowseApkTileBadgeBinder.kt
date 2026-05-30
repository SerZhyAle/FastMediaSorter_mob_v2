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
}