package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.hardware.camera2.CameraMetadata
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
 *
 * S1418: also the only place that decides when NORMAL is presented as "Manual". The manual mode is a
 * derived state of the user's own edits, not a device capability, so it cannot join [PhotoProfile]
 * without breaking that enum's contract that availability is a pure function of the capability
 * snapshot (strategic ADR-1) - the substitution belongs here, in presentation, instead.
 */
object CameraProfilePresentation {

    /** Exposure compensation is expressed in steps around the metered value; zero means automatic. */
    private const val AUTO_EXPOSURE_COMPENSATION_INDEX = 0

    @StringRes
    fun labelRes(profile: PhotoProfile, manual: Boolean = false): Int =
        if (manual && profile == PhotoProfile.NORMAL) {
            R.string.camera_profile_manual
        } else {
            defaultLabelRes(profile)
        }

    /**
     * S1418: true when the user has pulled exposure or white balance off automatic while shooting the
     * plain profile. Restricted to NORMAL on purpose - night and sport set their own exposure as part
     * of the recipe, so without this guard they would read as manual the moment they were picked.
     *
     * [whiteBalanceMode] is null while the session has never been told otherwise, i.e. automatic.
     */
    fun isManual(profile: PhotoProfile, exposureCompensationIndex: Int, whiteBalanceMode: Int?): Boolean {
        if (profile != PhotoProfile.NORMAL) return false
        val whiteBalanceManual = whiteBalanceMode != null &&
            whiteBalanceMode != CameraMetadata.CONTROL_AWB_MODE_AUTO
        return exposureCompensationIndex != AUTO_EXPOSURE_COMPENSATION_INDEX || whiteBalanceManual
    }

    @StringRes
    private fun defaultLabelRes(profile: PhotoProfile): Int = when (profile) {
        PhotoProfile.NORMAL -> R.string.camera_profile_normal
        PhotoProfile.DOCUMENT -> R.string.camera_profile_document
        PhotoProfile.NIGHT -> R.string.camera_profile_night
        PhotoProfile.PORTRAIT -> R.string.camera_profile_portrait
        PhotoProfile.SELFIE -> R.string.camera_profile_selfie
        PhotoProfile.MACRO -> R.string.camera_profile_macro
        PhotoProfile.SPORT -> R.string.camera_profile_sport
    }

    @DrawableRes
    fun iconRes(profile: PhotoProfile): Int = when (profile) {
        // S1417: ic_tune reads as "settings" everywhere else in the app (filter, edit-desktop), so on
        // the profile button it named the wrong thing. NORMAL is the plain still photo, and ic_image
        // is what a still already means here - media type IMAGE, the statistics rows, the widgets.
        PhotoProfile.NORMAL -> R.drawable.ic_image
        PhotoProfile.DOCUMENT -> R.drawable.ic_camera_profile_document
        PhotoProfile.NIGHT -> R.drawable.ic_camera_night_on
        PhotoProfile.PORTRAIT -> R.drawable.ic_camera_profile_portrait
        PhotoProfile.SELFIE -> R.drawable.ic_camera_profile_selfie
        PhotoProfile.MACRO -> R.drawable.ic_camera_macro_on
        PhotoProfile.SPORT -> R.drawable.ic_speed
    }
}
