package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.domain.model.MediaResource

interface BrowsePassthroughCaptureProvider {
    fun isAvailable(context: Context): Boolean
    fun launch(activity: FragmentActivity, resource: MediaResource, onFileSaved: (name: String) -> Unit)
}
