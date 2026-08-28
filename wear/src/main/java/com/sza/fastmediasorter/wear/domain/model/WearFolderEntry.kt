package com.sza.fastmediasorter.wear.domain.model

import android.net.Uri

/**
 * One row of a folder-walk level: either a directory to descend into, or a file to open.
 *
 * **Invariant.** A directory carries a non-null [address] and a null [uri]; a file carries the
 * reverse. The two are not interchangeable - an address names a level the repository can list, a uri
 * names content a player can open - and nothing in the walk needs a row that is both.
 *
 * S2201 ADR-3 requires files MediaStore never indexed to appear beside ones it did, so [uri] may be a
 * `file://` uri from the app-owned tree as well as a MediaStore content uri. A consumer that assumes
 * a content uri will fail on exactly the files this category exists to surface.
 */
data class WearFolderEntry(
    val name: String,
    val address: WearFolderAddress?,
    val uri: Uri?,
    val isDirectory: Boolean,
    val mimeType: String?,
    val sizeBytes: Long,
    val dateModifiedEpochSeconds: Long
)
