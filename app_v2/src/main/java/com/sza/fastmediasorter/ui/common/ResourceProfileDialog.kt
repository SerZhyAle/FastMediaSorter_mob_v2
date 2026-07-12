package com.sza.fastmediasorter.ui.common

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ResourceProfile

/**
 * The single resource-profile picker shared by the add-resource forms and the resource editor
 * (S1003). Both flows previously hand-rolled their own dialog (different titles, duplicated
 * label mapping) - any profile UI must go through this object so the dialog and its labels
 * cannot drift again.
 */
object ResourceProfileDialog {

    /** Display label for [profile] - the one mapping shared by buttons and the dialog list. */
    fun labelResId(profile: ResourceProfile): Int = when (profile) {
        ResourceProfile.NONE -> R.string.label_profile_none
        ResourceProfile.AUDIO_LIBRARY -> R.string.label_profile_audio_library
        ResourceProfile.VIDEO_LIBRARY -> R.string.label_profile_video_library
        ResourceProfile.PHOTO_STORAGE -> R.string.label_profile_photo_storage
        ResourceProfile.DOCUMENTS -> R.string.label_profile_documents
        ResourceProfile.ALL_FILES -> R.string.label_profile_all_files
    }

    /** Show the single-choice profile dialog; [onSelected] fires once with the picked profile. */
    fun show(context: Context, current: ResourceProfile, onSelected: (ResourceProfile) -> Unit) {
        val profiles = ResourceProfile.entries
        val labels = profiles.map { context.getString(labelResId(it)) }.toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.title_select_profile)
            .setSingleChoiceItems(labels, profiles.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                onSelected(profiles[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
