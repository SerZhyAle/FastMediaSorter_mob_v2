package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.usecase.SizeFilter

/**
 * Holds the last full cloud folder listing so the pages of one scan session and the folder count
 * are sliced from a single network walk instead of each re-listing the whole (possibly recursive)
 * folder. Cloud APIs expose no offset pagination, which is why a listing is walked whole at all.
 *
 * One entry only: a session pages through one folder at a time, and a different key replaces it.
 */
internal class CloudListingPageCache(
    private val clock: () -> Long = System::currentTimeMillis
) {

    data class Key(
        val path: String,
        val supportedTypes: Set<MediaType>,
        val sizeFilter: SizeFilter?,
        val scanSubdirectories: Boolean,
        val showHiddenFiles: Boolean
    )

    private class Entry(val key: Key, val files: List<MediaFile>, val storedAtMs: Long)

    @Volatile
    private var entry: Entry? = null

    /**
     * Returns the cached listing for [key] when it is younger than [reuseMaxAgeMs]; otherwise runs
     * [load] and stores its result. A null [reuseMaxAgeMs] always loads - the first page of a
     * session must see files added or moved since the previous one.
     */
    suspend fun getOrLoad(
        key: Key,
        reuseMaxAgeMs: Long?,
        load: suspend () -> List<MediaFile>
    ): List<MediaFile> {
        if (reuseMaxAgeMs != null) {
            lookup(key, reuseMaxAgeMs)?.let { return it }
        }
        val files = load()
        // An empty result is also what a swallowed scan failure returns; never serve it again.
        entry = if (files.isEmpty()) null else Entry(key, files, clock())
        return files
    }

    private fun lookup(key: Key, maxAgeMs: Long): List<MediaFile>? {
        val current = entry ?: return null
        val fresh = clock() - current.storedAtMs < maxAgeMs
        return current.files.takeIf { current.key == key && fresh }
    }
}
