package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.systeminfo.WearSystemInfoContributor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Assembles what the watch can say about itself by asking each contributor for its own sections.
 *
 * Runs off the main thread because the watch is stricter than the phone about battery and about a frame
 * it cannot draw. The contributors arrive as a multibound `Set`, which has no iteration order, so the
 * report is sorted by [WearSystemInfoContributor.order] - the order is observable behaviour and a test
 * asserts it.
 */
class GatherWearSystemInfoUseCase @Inject constructor(
    private val contributors: Set<@JvmSuppressWildcards WearSystemInfoContributor>
) {

    suspend operator fun invoke(): List<WearSystemInfoSection> = withContext(Dispatchers.IO) {
        Timber.d("S2165: assembling report from %d contributor(s)", contributors.size)
        contributors
            .sortedBy { contributor -> contributor.order }
            .flatMap { contributor -> sectionsOf(contributor) }
            .filter { section -> section.fields.isNotEmpty() || section.emptyReasonRes != null }
    }

    /**
     * A contributor is expected to swallow its own failures, but the report is opened when something
     * already looks wrong - so one that throws anyway costs its own sections and not the whole screen.
     */
    private suspend fun sectionsOf(contributor: WearSystemInfoContributor): List<WearSystemInfoSection> =
        runCatching { contributor.sections() }
            .onFailure { error ->
                Timber.w(error, "System info: %s failed", contributor::class.java.simpleName)
            }
            .getOrDefault(emptyList())
}
