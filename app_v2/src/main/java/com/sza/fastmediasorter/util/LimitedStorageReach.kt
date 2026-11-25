package com.sza.fastmediasorter.util

import android.content.Context
import com.sza.fastmediasorter.core.util.StoragePermissionRule
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_ALL_DOCS
import com.sza.fastmediasorter.data.local.LocalMediaScanner.Companion.VIRTUAL_PATH_RECENT
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import timber.log.Timber

/**
 * S2369: what a local resource can still deliver on a build that never declares all-files access.
 *
 * Without `MANAGE_EXTERNAL_STORAGE` - dropped from every store flavor by S2012 - MediaProvider hands
 * back only the rows the granular `READ_MEDIA_*` permissions cover. Document and binary rows are
 * removed inside the provider, before any app code sees the cursor, and the number removed does not
 * travel with it: the app cannot say how much is missing, and any wording that implies a count would
 * be guessing. What it can state exactly is how the folder is reached, which is a property of the
 * connection rather than of its contents and therefore stays true of an empty folder.
 */
object LimitedStorageReach {

    /** Rows MediaProvider still returns for a raw path when only the granular permissions are held. */
    val TYPES_REACHABLE_WITHOUT_ALL_FILES: Set<MediaType> = setOf(
        MediaType.IMAGE,
        MediaType.VIDEO,
        MediaType.AUDIO,
        MediaType.GIF
    )

    /**
     * True when [resource] is read through that narrowed provider AND undertakes to show at least one
     * type it cannot deliver. Both halves matter: a picture folder loses nothing to the narrowing, so
     * warning about it would be the same kind of lie pointing the other way.
     *
     * S4717: the notes folder is never limited. The Notes sub-program reads it through the file API, which
     * returns every note this install wrote without all-files access, so a tile saying "documents cannot be
     * read" would contradict the Notes screen that lists them.
     */
    fun isReachLimited(context: Context, resource: MediaResource): Boolean {
        // The resource-shape half is field reads; the platform half reaches AppOps through
        // `Environment.isExternalStorageManager`. Every resource row asks this while it binds, so the
        // cheap halves decide first and a resource nothing could narrow never reaches the platform.
        val narrowable = isNarrowable(resource.type, resource.path, promisedTypes(resource))
        val notesFolder = narrowable && NotesFolderPolicy.isNotesDirectory(resource.path)
        if (notesFolder) {
            Timber.d("S4717: notes folder exempt from the reach limit")
        }
        return narrowable && !notesFolder && !StoragePermissionRule.isAllFilesAccessHeld(context)
    }

    /** The rule itself, with every input supplied, so a test pins it without a device or a manifest. */
    fun isReachLimited(
        type: ResourceType,
        path: String,
        promisedTypes: Set<MediaType>,
        allFilesAccessMissing: Boolean,
        notesFolder: Boolean = false
    ): Boolean = isNarrowable(type, path, promisedTypes) && !notesFolder && allFilesAccessMissing

    /**
     * Whether the narrowing could bite this resource at all, judged from the resource alone. Split out
     * so both overloads read one copy of it - the screen and the test cannot drift apart about which
     * resources the rule even considers.
     */
    private fun isNarrowable(
        type: ResourceType,
        path: String,
        promisedTypes: Set<MediaType>
    ): Boolean = type == ResourceType.LOCAL &&
        isReadThroughMediaStore(path) &&
        promisedTypes.any { it !in TYPES_REACHABLE_WITHOUT_ALL_FILES }

    /** What the resource undertakes to show: everything when `allFiles` is on, its own set otherwise. */
    fun promisedTypes(resource: MediaResource): Set<MediaType> = when {
        resource.allFiles -> MediaType.entries.toSet()
        else -> resource.supportedMediaTypes
    }

    /** The part of [types] the narrowed provider can actually hand over. */
    fun narrowToReachable(types: Set<MediaType>): Set<MediaType> =
        types.intersect(TYPES_REACHABLE_WITHOUT_ALL_FILES)

    /**
     * A tree URI is served by the scanner's SAF branch, which returns documents today, so it is never
     * narrowed. Of the virtual aggregates only the two that promise documents are - the image, video,
     * audio and camera aggregates ask MediaStore for exactly the types it still returns.
     */
    private fun isReadThroughMediaStore(path: String): Boolean = when {
        path.startsWith(SAF_PATH_PREFIX) -> false
        !VirtualPathUtils.isVirtualPath(path) -> true
        else -> path == VIRTUAL_PATH_ALL_DOCS || path == VIRTUAL_PATH_RECENT
    }

    private const val SAF_PATH_PREFIX = "content://"
}
