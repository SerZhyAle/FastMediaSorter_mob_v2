package com.sza.fastmediasorter.ui.main.helpers

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.util.NotesFolderPolicy
import com.sza.fastmediasorter.util.VirtualPathUtils

/**
 * Decides which first-run hints the main screen shows (S4860).
 *
 * A fresh install is never an empty list: the app provisions the virtual aggregates, the Downloads
 * destination and, on demand, the watch and notes resources. "The user added something" therefore
 * means a resource none of those provisioning paths could have written.
 */
object MainFirstRunHintsPolicy {

    private const val FAVORITES_ENTRY_ID = -100L
    private const val CONTENT_SCHEME = "content://"

    fun showDestinationLegend(resources: List<MediaResource>): Boolean =
        resources.any { it.id != FAVORITES_ENTRY_ID && it.isDestination }

    fun hasAnyResource(resources: List<MediaResource>): Boolean =
        resources.any { it.id != FAVORITES_ENTRY_ID }

    /**
     * @param resolvePath maps a `content://` tree to a file path; a SAF grant re-made for Downloads
     *   is still the provisioned Downloads destination, not a folder the user added.
     */
    fun hasUserAddedResource(
        resources: List<MediaResource>,
        downloadsPath: String,
        resolvePath: (String) -> String?,
    ): Boolean = resources.any { resource ->
        resource.id != FAVORITES_ENTRY_ID && !isProvisioned(resource, downloadsPath, resolvePath)
    }

    private fun isProvisioned(
        resource: MediaResource,
        downloadsPath: String,
        resolvePath: (String) -> String?,
    ): Boolean {
        val path = resource.path
        return when {
            VirtualPathUtils.isVirtualPath(path) -> true
            resource.type == ResourceType.WEAR_WATCH -> true
            NotesFolderPolicy.isNotesDirectory(path) -> true
            path == downloadsPath -> true
            path.startsWith(CONTENT_SCHEME) -> resolvePath(path) == downloadsPath
            else -> false
        }
    }
}
