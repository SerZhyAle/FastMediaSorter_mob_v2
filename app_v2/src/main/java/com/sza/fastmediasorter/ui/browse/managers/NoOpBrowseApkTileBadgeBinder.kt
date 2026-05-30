package com.sza.fastmediasorter.ui.browse.managers

import android.view.View
import com.sza.fastmediasorter.domain.model.MediaFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpBrowseApkTileBadgeBinder @Inject constructor() : BrowseApkTileBadgeBinder {
    override fun bind(root: View, mediaFile: MediaFile) = Unit
}