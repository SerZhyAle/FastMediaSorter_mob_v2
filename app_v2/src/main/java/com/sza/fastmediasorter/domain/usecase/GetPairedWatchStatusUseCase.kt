package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.PairedWatchStatus
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Answer "which watch is on the link right now" for the Wear companion settings group (S1885).
 *
 * Reads the same connected-node list the four send paths already use rather than introducing an
 * "identify yourself" exchange: the bridge answers this question directly, so a new event, schema
 * and reply timeout would buy nothing.
 */
class GetPairedWatchStatusUseCase @Inject constructor(
    private val wearableRepository: WearableDataLayerRepository,
) {

    suspend operator fun invoke(): PairedWatchStatus {
        val name = wearableRepository.getConnectedNodes()
            .firstOrNull()
            ?.displayName
            ?.takeIf { it.isNotBlank() }
            // S2868: the raw display name carries the model code in parentheses, and the UI names the
            // watch by the human part only.
            ?.let(::watchDisplayNameWithoutModelCode)
        // A blank display name is treated as no watch: a row naming an empty string reads as a bug,
        // and "not connected" is the honest answer when the bridge cannot say who answered.
        Timber.d("S2868: paired-watch row name resolved: name=%s", name ?: "<none>")
        return if (name == null) PairedWatchStatus.NotConnected else PairedWatchStatus.Connected(name)
    }
}

/**
 * The bridge renders a node as "Galaxy Watch7 (8CRZ)" - the parenthetical is a diagnostics tail that
 * S2868 keeps out of user-visible labels, not part of the name. A display name without a
 * parenthetical passes through unchanged, and an empty result falls back to the input.
 */
fun watchDisplayNameWithoutModelCode(displayName: String): String {
    val withoutModelCode = displayName.substringBefore('(').trim()
    return withoutModelCode.ifBlank { displayName.trim() }
}
