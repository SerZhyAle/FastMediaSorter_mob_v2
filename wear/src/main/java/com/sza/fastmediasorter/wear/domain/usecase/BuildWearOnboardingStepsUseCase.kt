package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.onboarding.WearOnboardingPermissionStep
import com.sza.fastmediasorter.wear.domain.onboarding.WearOnboardingPlannedStep
import javax.inject.Inject

/**
 * Chooses the first-run permission steps for this build on this watch.
 *
 * A permission counts only when the merged manifest declares it and the platform knows it at
 * [sdkInt]; a step left with nothing to ask is dropped, so an edition that declares no sensitive
 * permission goes from the welcome page straight to the app.
 */
class BuildWearOnboardingStepsUseCase @Inject constructor() {

    operator fun invoke(declared: Set<String>, sdkInt: Int): List<WearOnboardingPlannedStep> =
        WearOnboardingPermissionStep.entries.mapNotNull { step ->
            val permissions = step.candidates
                .filter { it.permission in declared && sdkInt in it.minSdk..it.maxSdk }
                .map { it.permission }
            if (permissions.isEmpty()) null else WearOnboardingPlannedStep(step, permissions)
        }
}
