package com.sza.fastmediasorter.wear.domain.onboarding

import android.Manifest
import android.os.Build

/**
 * One permission a step may ask for, and the API range in which the platform knows it.
 *
 * The bounds mirror the `minSdkVersion` / `maxSdkVersion` attributes of the flavor manifest: outside
 * them the permission is either unknown to the platform or replaced by its successor, so asking for it
 * would read back as a refusal that never happened.
 */
data class WearOnboardingPermissionCandidate(
    val permission: String,
    val minSdk: Int = Build.VERSION_CODES.BASE,
    val maxSdk: Int = Int.MAX_VALUE,
)

/**
 * The first-run permission walk, in the order the steps are asked.
 *
 * A step is one group the user decides about once - the three media permissions are one question,
 * not three. Whether a build asks a step at all is decided by its merged manifest, never here
 * (S3186 ADR-1): the store edition declares none of these and so walks none of them.
 */
enum class WearOnboardingPermissionStep(val candidates: List<WearOnboardingPermissionCandidate>) {
    MEDIA(
        listOf(
            WearOnboardingPermissionCandidate(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                maxSdk = Build.VERSION_CODES.S_V2
            ),
            WearOnboardingPermissionCandidate(
                Manifest.permission.READ_MEDIA_AUDIO,
                minSdk = Build.VERSION_CODES.TIRAMISU
            ),
            WearOnboardingPermissionCandidate(
                Manifest.permission.READ_MEDIA_VIDEO,
                minSdk = Build.VERSION_CODES.TIRAMISU
            ),
            WearOnboardingPermissionCandidate(
                Manifest.permission.READ_MEDIA_IMAGES,
                minSdk = Build.VERSION_CODES.TIRAMISU
            ),
        )
    ),
    MICROPHONE(listOf(WearOnboardingPermissionCandidate(Manifest.permission.RECORD_AUDIO))),
    NOTIFICATIONS(
        listOf(
            WearOnboardingPermissionCandidate(
                Manifest.permission.POST_NOTIFICATIONS,
                minSdk = Build.VERSION_CODES.TIRAMISU
            )
        )
    ),
    HEART_RATE(
        listOf(
            WearOnboardingPermissionCandidate(
                Manifest.permission.BODY_SENSORS,
                maxSdk = Build.VERSION_CODES.VANILLA_ICE_CREAM
            ),
            WearOnboardingPermissionCandidate(PERMISSION_READ_HEART_RATE, minSdk = Build.VERSION_CODES.BAKLAVA),
        )
    ),

    // Below API 29 activity recognition is granted at install time, so there is nothing to ask.
    ACTIVITY(
        listOf(
            WearOnboardingPermissionCandidate(
                Manifest.permission.ACTIVITY_RECOGNITION,
                minSdk = Build.VERSION_CODES.Q
            )
        )
    ),
    NEARBY_DEVICES(
        listOf(
            WearOnboardingPermissionCandidate(
                Manifest.permission.BLUETOOTH_CONNECT,
                minSdk = Build.VERSION_CODES.S
            ),
            WearOnboardingPermissionCandidate(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                minSdk = Build.VERSION_CODES.TIRAMISU
            ),
        )
    ),
}

/** A step this build will actually ask, with only the permissions that apply on this watch. */
data class WearOnboardingPlannedStep(
    val step: WearOnboardingPermissionStep,
    val permissions: List<String>,
)

private const val PERMISSION_READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
