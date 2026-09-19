package com.sza.fastmediasorter.wear.domain.permission

import com.sza.fastmediasorter.wear.domain.onboarding.WearOnboardingPermissionStep

/**
 * A permission the settings screen lets the user revisit after the install, and the first-run steps it
 * is made of.
 *
 * The three groups are the owner's quiz ruling for S3226: media, the microphone behind voice notes, and
 * the body sensors the activity programs read. Heart rate and activity recognition are one row here and
 * two pages during onboarding, because a user who comes looking for "sensors" after the install is
 * looking for one switch, not for the order the first-run walk asked in.
 */
enum class WearPermissionGroup(val steps: List<WearOnboardingPermissionStep>) {
    MEDIA(listOf(WearOnboardingPermissionStep.MEDIA)),
    MICROPHONE(listOf(WearOnboardingPermissionStep.MICROPHONE)),
    SENSORS(listOf(WearOnboardingPermissionStep.HEART_RATE, WearOnboardingPermissionStep.ACTIVITY)),
}

/** A group this build can actually ask for, with only the permissions that apply on this watch. */
data class WearPlannedPermissionGroup(
    val group: WearPermissionGroup,
    val permissions: List<String>,
)
