package com.sza.fastmediasorter.core.share.handlers

import android.app.Activity
import com.sza.fastmediasorter.core.share.SharePrintHost
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareableContent
import javax.inject.Inject

/**
 * Print receiver (S0459 ADR-10): host-bound. Dispatches to the host Activity's [SharePrintHost]
 * (the player wires it to its DocumentPrintManager). A host that does not implement the interface,
 * or content without a [ShareableContent.mediaFile], returns false so Print stays inert there.
 */
class PrintShareTargetHandler @Inject constructor() : ShareTargetHandler {

    override val targetId: String = ID

    // ADR-10: gate Print at menu-build time so it never appears on a host that cannot print
    // (otherwise the row would render and silently no-op when send() returns false).
    override fun isSupportedBy(activity: Activity): Boolean = activity is SharePrintHost

    override fun send(activity: Activity, content: ShareableContent): Boolean {
        val host = activity as? SharePrintHost ?: return false
        val mediaFile = content.mediaFile ?: return false
        return host.printMediaFile(mediaFile)
    }

    companion object {
        const val ID = "print"
    }
}
