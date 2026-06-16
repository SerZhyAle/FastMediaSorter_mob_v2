package com.sza.fastmediasorter.core.share

import android.net.Uri
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType

/**
 * What a surface hands to a [ShareTargetHandler], decoupled from any Activity or player state
 * (S0459). A surface builds this from its current file(s); a handler consumes only this - never
 * global UI state - so the same handler works from the player, browse, or a standalone host.
 */
data class ShareableContent(
    val uris: List<Uri>,
    val mime: String,
    val mediaType: MediaType,
    /** Plain-text payload for text-only receivers (e.g. Keep-text); null for binary receivers. */
    val text: String? = null,
    /** Optional human-readable name used as a subject/title by some receivers (e.g. email). */
    val displayName: String? = null,
    /**
     * Source domain file, for receivers that need more than a shareable Uri - notably Print, handed
     * to the host's [SharePrintHost.printMediaFile]. Uri-based receivers ignore it. Null when the
     * surface only has Uris (S0459 ADR-11).
     */
    val mediaFile: MediaFile? = null,
) {
    /** Content scoped to the first file - used by single-file receivers on a multi-selection (ADR-4). */
    fun single(): ShareableContent =
        if (uris.size <= 1) this else copy(uris = listOf(uris.first()))
}
