package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.share.ShareTarget
import com.sza.fastmediasorter.core.share.ShareTargetAvailabilityResolver
import com.sza.fastmediasorter.core.share.ShareTargetRegistry
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.core.share.appliesTo
import com.sza.fastmediasorter.domain.model.AppSettings
import javax.inject.Inject

/**
 * Builds the ordered, gated list of receivers shown in the «Send to..» menu (S0459 Phase 04).
 *
 * Three-gate filter: a receiver is offered when it is (1) enabled in settings, (2) available on
 * this device, and (3) applies to the content's media type. Order comes straight from
 * [ShareTargetRegistry.all] - the fixed, grouped canonical order shared with the settings toggle
 * group - so filtering preserves a receiver's position regardless of usage (owner 2026-06-17: a
 * fixed position is friendlier to muscle memory than usage-frequency reordering).
 *
 * For a multi-file selection the caller sets content.mediaType to the first file's type, so
 * single-only receivers are correctly gated here without additional logic (ADR-4).
 */
class BuildSendToReceiverListUseCase @Inject constructor(
    private val registry: ShareTargetRegistry,
    private val resolver: ShareTargetAvailabilityResolver,
    private val isEnabled: IsShareTargetEnabledUseCase,
) {
    operator fun invoke(content: ShareableContent, settings: AppSettings): List<ShareTarget> =
        registry.all()
            .filter { target ->
                isEnabled(target.id, settings)
                    && resolver.isAvailable(target)
                    && target.appliesTo(content.mediaType)
            }
}
