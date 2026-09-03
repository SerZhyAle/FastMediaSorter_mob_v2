package com.sza.fastmediasorter.wear.domain.files

import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiverEntry

/**
 * S2142: which of the published receivers a given selection may actually be handed to.
 *
 * One object rather than a copy per screen, for the reason strategic 11 criterion 4 states about the
 * menus above it: three surfaces open the receiver list, and the same file has to offer the same
 * receivers on each. Two screens filtering slightly differently is exactly the silent divergence the
 * criterion exists to prevent.
 *
 * Both filters read fields the phone published with the record, so neither is a second rule that can
 * drift from the phone's own: a receiver that cannot take what is selected would open onto a
 * refusal, which ADR-3 forbids for a receiver as firmly as for an operation.
 */
object WearSendToReceiverFilter {

    fun apply(
        receivers: List<WearSendToReceiverEntry>,
        selected: List<WearMediaFile>
    ): List<WearSendToReceiverEntry> {
        if (selected.isEmpty()) {
            return emptyList()
        }
        return receivers.filter { entry ->
            (selected.size == 1 || entry.batchCapable) && entry.acceptsAll(selected)
        }
    }

    /**
     * An empty [WearSendToReceiverEntry.applicableTypes] means "any type", which is how a receiver
     * that takes whatever it is given - the system share sheet, the clipboard - is declared.
     */
    private fun WearSendToReceiverEntry.acceptsAll(selected: List<WearMediaFile>): Boolean =
        applicableTypes.isEmpty() || selected.all { file -> applicableTypes.any { file.matchesType(it) } }

    /**
     * The published type name against one file's MIME type, compared by family.
     *
     * The phone names a family - the media type it filters its own menu by - rather than an exact
     * type, so an exact-string match would reject `image/heic` from a receiver that declared images.
     */
    private fun WearMediaFile.matchesType(publishedType: String): Boolean {
        val mime = mimeType ?: return false
        return when (publishedType.substringBefore('/').lowercase()) {
            "photo", "image" -> mime.startsWith("image/")
            "video" -> mime.startsWith("video/")
            "music", "audio" -> mime.startsWith("audio/")
            "text" -> mime.startsWith("text/")
            else -> mime.equals(publishedType, ignoreCase = true)
        }
    }
}
