package com.sza.fastmediasorter.ui.launcher.gadget

import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0404: the set of gadgets the desktop knows, and the codec for a GADGET cell's `target` column.
 *
 * The encoding mirrors [LauncherCellCommand][com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand]'s
 * prefix scheme (`<key>` or `<key>:<param>`) rather than inventing a second one, so both cell kinds
 * stay one TEXT column with no schema change.
 */
@Singleton
class LauncherGadgetRegistry @Inject constructor(
    clock: ClockGadget,
    playlist: PlaylistGadget,
    streams: StreamsGadget,
    folderPreview: FolderPreviewGadget,
) {

    private val gadgets: List<LauncherGadget> = listOf(clock, playlist, streams, folderPreview)

    /** Picker order (Phase 07): cheapest and most universal first. */
    fun all(): List<LauncherGadget> = gadgets

    fun byKey(key: String): LauncherGadget? = gadgets.firstOrNull { it.key == key }

    fun encodeTarget(key: String, param: String?): String =
        if (param.isNullOrBlank()) key else "$key$SEPARATOR$param"

    /**
     * Splits on the FIRST separator only - a param may legitimately contain one, and the key never
     * does. Returns null for a blank target; an unknown key is the caller's problem to render (the
     * registry does not decide what a broken cell looks like).
     */
    fun decodeTarget(raw: String?): Pair<String, String?>? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val separator = value.indexOf(SEPARATOR)
        if (separator < 0) return value to null
        val key = value.substring(0, separator)
        if (key.isEmpty()) return null
        val param = value.substring(separator + 1).takeIf { it.isNotBlank() }
        return key to param
    }

    companion object {
        const val KEY_CLOCK = "clock"
        const val KEY_PLAYLIST = "playlist"
        const val KEY_STREAMS = "streams"
        const val KEY_FOLDER_PREVIEW = "folder_preview"

        private const val SEPARATOR = ':'
    }
}
