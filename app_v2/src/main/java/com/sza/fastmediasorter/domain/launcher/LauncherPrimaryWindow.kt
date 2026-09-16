package com.sza.fastmediasorter.domain.launcher

import com.sza.fastmediasorter.data.model.DeviceProfileType

/**
 * Primary window choices presented during onboarding and in settings.
 */
enum class LauncherPrimaryWindow {
    HOME_SCREEN,
    DESKTOP,
    RESOURCE_MANAGER;

    companion object {
        /**
         * Recommends the default primary window for the given device profile.
         */
        fun recommendedFor(profileType: DeviceProfileType?): LauncherPrimaryWindow {
            return when (profileType) {
                DeviceProfileType.PERSONAL_SMARTPHONE,
                DeviceProfileType.HOME_TABLET,
                DeviceProfileType.TV_MEDIA_BOX,
                DeviceProfileType.CAR_HEAD_UNIT -> HOME_SCREEN
                DeviceProfileType.MEDIA_PLAYER,
                DeviceProfileType.PHOTO_FRAME,
                DeviceProfileType.VIDEO_PLAYER,
                DeviceProfileType.AUDIO_PLAYER,
                DeviceProfileType.EBOOK_READER,
                DeviceProfileType.VR_HEADSET,
                DeviceProfileType.OTHER,
                null -> RESOURCE_MANAGER
            }
        }
    }
}
