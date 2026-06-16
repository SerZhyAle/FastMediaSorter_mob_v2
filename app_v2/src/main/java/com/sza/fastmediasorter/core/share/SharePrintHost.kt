package com.sza.fastmediasorter.core.share

import com.sza.fastmediasorter.domain.model.MediaFile

/**
 * Host capability for the Print receiver of the unified «Send to..» menu (S0459 ADR-10).
 *
 * Printing is bound to its host - the player's `DocumentPrintManager` needs the player Activity - so
 * unlike Uri-based receivers it cannot be a context-free [ShareTargetHandler]. A host that can print
 * implements this; `PrintShareTargetHandler` dispatches through it. A host that does not implement it
 * never prints, so the Print receiver simply stays hidden there.
 */
interface SharePrintHost {
    /** @return true when a print job was dispatched for [mediaFile]. */
    fun printMediaFile(mediaFile: MediaFile): Boolean
}
