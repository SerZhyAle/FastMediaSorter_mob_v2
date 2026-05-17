package com.sza.fastmediasorter.util

import com.sza.fastmediasorter.core.files.FileNameDefaultProvider
import java.time.ZoneId

/**
 * S0189: produces the default filename for a new text note (`yy-MM-dd_HH-mm.txt`).
 *
 * Thin wrapper over the shared [FileNameDefaultProvider] (S0189 Phase 09 extraction).
 * Kept to preserve the call-site surface for text-note flows; S0191 (drawings) wires its
 * own provider instance with extension `"png"` directly through [FileNameDefaultProvider].
 */
object TextNoteFileNameProvider {

    private val delegate = FileNameDefaultProvider(extension = "txt")

    /**
     * @param now  epoch millis to base the name on (default: current time)
     * @param zone time zone (default: device default)
     * @return filename like `26-05-16_14-32.txt`
     */
    fun defaultName(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = delegate.defaultName(now, zone)
}
