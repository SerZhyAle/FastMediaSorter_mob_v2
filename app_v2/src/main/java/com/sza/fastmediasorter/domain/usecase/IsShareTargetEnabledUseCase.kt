package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.share.ShareTargetAvailabilityResolver
import com.sza.fastmediasorter.core.share.ShareTargetRegistry
import com.sza.fastmediasorter.domain.model.AppSettings
import javax.inject.Inject

/**
 * Effective on/off state of a share target (S0452), the single source of truth for both the
 * settings toggle and the menu-visibility gate.
 *
 * Explicit user choice wins over the registry default; this keeps "user turned OFF a default-ON
 * target" persistent. Visibility additionally requires [ShareTargetAvailabilityResolver.isAvailable].
 */
class IsShareTargetEnabledUseCase @Inject constructor(
    private val registry: ShareTargetRegistry,
    private val resolver: ShareTargetAvailabilityResolver,
) {
    /** @return whether [targetId] is enabled given the user's [settings], or false for an unknown id. */
    operator fun invoke(targetId: String, settings: AppSettings): Boolean {
        val target = registry.byId(targetId) ?: return false
        return when (targetId) {
            in settings.enabledShareTargets -> true
            in settings.disabledShareTargets -> false
            else -> resolver.isDefaultEnabled(target)
        }
    }
}
