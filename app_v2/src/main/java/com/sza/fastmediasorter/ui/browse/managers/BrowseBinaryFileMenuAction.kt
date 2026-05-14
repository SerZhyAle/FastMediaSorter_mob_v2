package com.sza.fastmediasorter.ui.browse.managers

import android.view.View
import androidx.activity.ComponentActivity
import com.sza.fastmediasorter.domain.model.MediaFile

/**
 * Flavor-specific extension point for Browse binary-file bottom sheet actions.
 *
 * Keeping these hooks generic lets src/main stay free of noLegal-only UI and behavior.
 */
interface BrowseBinaryFileMenuAction {

    fun registerLaunchers(activity: ComponentActivity) = Unit

    fun bind(view: View, mediaFile: MediaFile, onDismiss: () -> Unit)
}