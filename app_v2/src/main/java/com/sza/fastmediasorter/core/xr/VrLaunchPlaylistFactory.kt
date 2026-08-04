package com.sza.fastmediasorter.core.xr

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType

/**
 * S1233: builds the ordered list the immersive HUD's PREV/NEXT walk, in the order the calling
 * surface is already showing it, so "next" means the same thing in the flat and immersive surfaces.
 *
 * Entries the immersive host cannot open are dropped here rather than guarded there: the host
 * resolves a local [java.io.File] and has no SMB/SFTP/cloud client, and giving it one is exactly the
 * dependency this design avoids. A network-only or cloud-only resource therefore yields an empty
 * list, and the host keeps its pre-S1233 single-file behaviour.
 *
 * One home for the rule on purpose - the player badge and the Browse "Open in VR Cinema" entry both
 * call this, so the two entry points cannot drift into disagreeing about what is navigable.
 */
internal object VrLaunchPlaylistFactory {

    /** Below this there is nothing to navigate, and an empty list keeps the host single-file. */
    private const val MIN_NAVIGABLE_SIZE = 2

    private const val FILE_URI_PREFIX = "file://"

    private val NOT_NAVIGABLE: Pair<List<VrPlaylistEntry>, Int> = emptyList<VrPlaylistEntry>() to -1

    /**
     * @return the navigable entries paired with [current]'s index inside them, or an empty list with
     * index -1 when there is nothing worth navigating.
     */
    fun build(files: List<MediaFile>, current: MediaFile): Pair<List<VrPlaylistEntry>, Int> {
        val navigable = files.filter { it.isImmersiveNavigable() }
        val index = navigable.indexOfFirst { it.path == current.path }
        // A list the launched file is not part of would send PREV/NEXT to an unrelated frame, and a
        // single-entry list is the thing S1233 exists to stop dressing up as a playlist.
        if (index < 0 || navigable.size < MIN_NAVIGABLE_SIZE) return NOT_NAVIGABLE
        val entries = navigable.map { file ->
            VrPlaylistEntry(fileUriString = file.toLaunchUriString(), mediaType = file.toVrMediaType())
        }
        return entries to index
    }

    // GIF is excluded because the host's prepareLaunchMedia short-circuits it, so navigating onto one
    // would strand the session on a file it refuses to show.
    private fun MediaFile.isImmersiveNavigable(): Boolean =
        !isDirectory &&
            (type == MediaType.IMAGE || type == MediaType.VIDEO) &&
            (path.startsWith("/") || path.startsWith(FILE_URI_PREFIX))

    /** Safe because [isImmersiveNavigable] has already reduced the set to IMAGE and VIDEO. */
    private fun MediaFile.toVrMediaType(): VrMediaType =
        if (type == MediaType.VIDEO) VrMediaType.VIDEO else VrMediaType.IMAGE
}
