package com.sza.fastmediasorter.ui.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.model.DeviceProfileType

/**
 * Single source of truth mapping a [DeviceProfileType] to its UI resources (icon, title,
 * description).
 *
 * Shared by the Welcome flow, the Settings entry and the device-profile picker dialog so the
 * icon / title / description set is defined in exactly one place. Add a new profile's resources
 * here once and every surface picks them up.
 */
object DeviceProfileUi {

    /** All selectable profiles, in the order they should appear in the picker grid. */
    val displayOrder: List<DeviceProfileType> = DeviceProfileType.values().toList()

    @DrawableRes
    fun iconRes(type: DeviceProfileType): Int = when (type) {
        DeviceProfileType.PERSONAL_SMARTPHONE -> R.drawable.ic_profile_personal_smartphone
        DeviceProfileType.HOME_TABLET -> R.drawable.ic_profile_home_tablet
        DeviceProfileType.TV_MEDIA_BOX -> R.drawable.ic_profile_tv_media_box
        DeviceProfileType.CAR_HEAD_UNIT -> R.drawable.ic_profile_car_head_unit
        DeviceProfileType.MEDIA_PLAYER -> R.drawable.ic_profile_media_player
        DeviceProfileType.PHOTO_FRAME -> R.drawable.ic_profile_photo_frame
        DeviceProfileType.VIDEO_PLAYER -> R.drawable.ic_profile_video_player
        DeviceProfileType.AUDIO_PLAYER -> R.drawable.ic_profile_audio_player
        DeviceProfileType.EBOOK_READER -> R.drawable.ic_profile_ebook_reader
        DeviceProfileType.VR_HEADSET -> R.drawable.ic_profile_vr_headset
        DeviceProfileType.OTHER -> R.drawable.ic_profile_other
    }

    @StringRes
    fun titleRes(type: DeviceProfileType): Int = when (type) {
        DeviceProfileType.PERSONAL_SMARTPHONE -> R.string.welcome_profile_personal_smartphone_title
        DeviceProfileType.HOME_TABLET -> R.string.welcome_profile_home_tablet_title
        DeviceProfileType.TV_MEDIA_BOX -> R.string.welcome_profile_tv_media_box_title
        DeviceProfileType.CAR_HEAD_UNIT -> R.string.welcome_profile_car_head_unit_title
        DeviceProfileType.MEDIA_PLAYER -> R.string.welcome_profile_media_player_title
        DeviceProfileType.PHOTO_FRAME -> R.string.welcome_profile_photo_frame_title
        DeviceProfileType.VIDEO_PLAYER -> R.string.welcome_profile_video_player_title
        DeviceProfileType.AUDIO_PLAYER -> R.string.welcome_profile_audio_player_title
        DeviceProfileType.EBOOK_READER -> R.string.welcome_profile_ebook_reader_title
        DeviceProfileType.VR_HEADSET -> R.string.welcome_profile_vr_headset_title
        DeviceProfileType.OTHER -> R.string.welcome_profile_other_title
    }

    @StringRes
    fun descRes(type: DeviceProfileType): Int = when (type) {
        DeviceProfileType.PERSONAL_SMARTPHONE -> R.string.welcome_profile_personal_smartphone_desc
        DeviceProfileType.HOME_TABLET -> R.string.welcome_profile_home_tablet_desc
        DeviceProfileType.TV_MEDIA_BOX -> R.string.welcome_profile_tv_media_box_desc
        DeviceProfileType.CAR_HEAD_UNIT -> R.string.welcome_profile_car_head_unit_desc
        DeviceProfileType.MEDIA_PLAYER -> R.string.welcome_profile_media_player_desc
        DeviceProfileType.PHOTO_FRAME -> R.string.welcome_profile_photo_frame_desc
        DeviceProfileType.VIDEO_PLAYER -> R.string.welcome_profile_video_player_desc
        DeviceProfileType.AUDIO_PLAYER -> R.string.welcome_profile_audio_player_desc
        DeviceProfileType.EBOOK_READER -> R.string.welcome_profile_ebook_reader_desc
        DeviceProfileType.VR_HEADSET -> R.string.welcome_profile_vr_headset_desc
        DeviceProfileType.OTHER -> R.string.welcome_profile_other_desc
    }
}
