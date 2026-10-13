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
        /** The choices a surface may offer; HOME_SCREEN is dropped where the role cannot be granted. */
        fun choicesFor(homeRoleOfferable: Boolean): List<LauncherPrimaryWindow> =
            if (homeRoleOfferable) entries else entries - HOME_SCREEN

        /**
         * Recommends the default primary window for the given device profile.
         *
         * A desktop platform (Googlebook) defaults to the in-app desktop for every profile. Elsewhere
         * the profile mapping stands, except that a HOME_SCREEN pick degrades to DESKTOP where the
         * role is not offerable (XR keeps its other recommendations unchanged).
         */
        fun recommendedFor(
            profileType: DeviceProfileType?,
            homeRoleOfferable: Boolean = true,
            desktopPlatform: Boolean = false,
        ): LauncherPrimaryWindow {
            if (desktopPlatform) return DESKTOP
            val byProfile = when (profileType) {
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
            return if (byProfile == HOME_SCREEN && !homeRoleOfferable) DESKTOP else byProfile
        }
    }
}
