package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.onboarding.WearOnboardingPermissionStep
import com.sza.fastmediasorter.wear.domain.onboarding.WearOnboardingPlannedStep
import javax.inject.Inject

/**
 * Chooses the first-run permission steps for this build on this watch.
 *
 * A permission counts only when the merged manifest declares it and the platform knows it at
 * [sdkInt]; a step left with nothing to ask is dropped, so an edition that declares no sensitive
 * permission goes from the welcome page straight to the app.
 *
 * S3555: the one step the manifest does not decide alone is [WearOnboardingPermissionStep.NOTIFICATIONS].
 * The store edition declares the permission for a single reason - the running stopwatch's ongoing
 * activity - and asks for it at the first start of a measurement, where its purpose is obvious. Walking
 * it up front would also draw the welcome page, whose copy promises media this edition does not reach
 * (S3362), so the step is walked only by a build that records, where the notification must be allowed
 * before the first recording starts.
 */
class BuildWearOnboardingStepsUseCase @Inject constructor(
    private val capabilities: WearRestrictedCapabilities
) {

    operator fun invoke(declared: Set<String>, sdkInt: Int): List<WearOnboardingPlannedStep> =
        WearOnboardingPermissionStep.entries
            .filter { it != WearOnboardingPermissionStep.NOTIFICATIONS || capabilities.offersVoiceRecording }
            .mapNotNull { step ->
                val permissions = step.candidates
                    .filter { it.permission in declared && sdkInt in it.minSdk..it.maxSdk }
                    .map { it.permission }
                if (permissions.isEmpty()) null else WearOnboardingPlannedStep(step, permissions)
            }
}
