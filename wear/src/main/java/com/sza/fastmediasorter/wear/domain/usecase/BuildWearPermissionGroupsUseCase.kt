package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.permission.WearPermissionGroup
import com.sza.fastmediasorter.wear.domain.permission.WearPlannedPermissionGroup
import javax.inject.Inject

/**
 * Chooses the permission rows the settings screen shows on this build and this watch.
 *
 * The decision of what may be asked at all belongs to the merged manifest, never to a flavor check in
 * shared code (S3186 ADR-1), so this reuses the first-run planner rather than repeating its rule: an
 * edition that declares no sensitive permission plans no step and therefore shows no row, and the menu
 * entry above it disappears with them instead of promising a screen with nothing on it (S3178).
 */
class BuildWearPermissionGroupsUseCase @Inject constructor(
    private val buildOnboardingSteps: BuildWearOnboardingStepsUseCase
) {

    operator fun invoke(declared: Set<String>, sdkInt: Int): List<WearPlannedPermissionGroup> {
        val byStep = buildOnboardingSteps(declared, sdkInt).associate { it.step to it.permissions }
        return WearPermissionGroup.entries.mapNotNull { group ->
            val permissions = group.steps.flatMap { byStep[it].orEmpty() }
            if (permissions.isEmpty()) null else WearPlannedPermissionGroup(group, permissions)
        }
    }
}
