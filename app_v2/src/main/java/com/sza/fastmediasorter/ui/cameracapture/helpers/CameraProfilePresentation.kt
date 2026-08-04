package com.sza.fastmediasorter.ui.cameracapture.helpers

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.cameracapture.model.PhotoProfile

/**
 * S1262: resource mapping for the photo-profile menu - the only place that decides how a profile is
 * named and drawn, so the button and the menu row can never disagree.
 *
 * Night and macro reuse the icons their retired standalone toggles carried, so a user who knew those
 * buttons recognises the same glyphs inside the menu.
 */
object CameraProfilePresentation {

    @StringRes
    fun labelRes(profile: PhotoProfile): Int = when (profile) {
        PhotoProfile.NORMAL -> R.string.camera_profile_normal
        PhotoProfile.NIGHT -> R.string.camera_profile_night
        PhotoProfile.PORTRAIT -> R.string.camera_profile_portrait
        PhotoProfile.SELFIE -> R.string.camera_profile_selfie
        PhotoProfile.MACRO -> R.string.camera_profile_macro
        PhotoProfile.SPORT -> R.string.camera_profile_sport
    }

    @DrawableRes
    fun iconRes(profile: PhotoProfile): Int = when (profile) {
        PhotoProfile.NORMAL -> R.drawable.ic_tune
        PhotoProfile.NIGHT -> R.drawable.ic_camera_night_on
        PhotoProfile.PORTRAIT -> R.drawable.ic_camera_profile_portrait
        PhotoProfile.SELFIE -> R.drawable.ic_camera_profile_selfie
        PhotoProfile.MACRO -> R.drawable.ic_camera_macro_on
        PhotoProfile.SPORT -> R.drawable.ic_speed
    }
}
