package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.StorageVolumeInfo
import com.sza.fastmediasorter.domain.repository.StorageVolumeRepository
import java.net.URLDecoder
import javax.inject.Inject

/**
 * Free bytes at a copy/move destination, measured on the volume that actually receives the data.
 *
 * A destination arrives in one of two shapes - a filesystem path or a document-tree address - and
 * both have to resolve to the same registry entry. Measuring only the primary volume is what lets a
 * write to a card be checked against built-in storage and pass a fit check it cannot honour.
 *
 * Returns null when no known volume claims the destination: the caller has to tell "no room" apart
 * from "could not measure", and only the first of those may refuse an operation.
 */
class GetDestinationFreeSpaceUseCase @Inject constructor(
    private val storageVolumeRepository: StorageVolumeRepository
) {

    suspend operator fun invoke(destination: String): Long? =
        volumeFor(destination)?.takeIf { it.isMounted }?.availableBytes

    /** The volume [destination] lives on, or null when no known volume claims it. */
    suspend fun volumeFor(destination: String): StorageVolumeInfo? {
        val treeVolumeId = treeVolumeId(destination)
        if (treeVolumeId != null) {
            return storageVolumeRepository.getVolume(treeVolumeId)
        }
        return mountedVolumeOwning(destination)
    }

    /**
     * Longest matching mount path wins: several volumes can share a prefix, and the deepest mount
     * containing the path is the one whose free space the write actually consumes.
     */
    private suspend fun mountedVolumeOwning(path: String): StorageVolumeInfo? =
        storageVolumeRepository.getVolumes()
            .filter { it.isMounted && isUnder(path, it.mountPath) }
            .maxByOrNull { it.mountPath?.length ?: 0 }

    private fun isUnder(path: String, mountPath: String?): Boolean {
        if (mountPath.isNullOrEmpty() || !path.startsWith(mountPath)) return false
        // A mount path must end at a segment boundary, otherwise /storage/emulated/0 would claim
        // a sibling volume mounted at /storage/emulated/01.
        return path.length == mountPath.length || path[mountPath.length] == PATH_SEPARATOR
    }

    /**
     * Volume id of a document-tree address, or null when [destination] is not one.
     *
     * Parsed from the string rather than through `Uri`/`DocumentsContract` so the measurement stays
     * a plain JVM function and its test needs no Android runtime.
     */
    private fun treeVolumeId(destination: String): String? {
        if (!destination.startsWith(CONTENT_SCHEME)) return null
        val encoded = destination.substringAfter(TREE_SEGMENT, "").substringBefore(PATH_SEPARATOR)
        val documentId = if (encoded.isEmpty()) "" else URLDecoder.decode(encoded, ENCODING)
        return documentId.substringBefore(VOLUME_SEPARATOR, "").takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val CONTENT_SCHEME = "content://"
        const val TREE_SEGMENT = "/tree/"
        const val PATH_SEPARATOR = '/'
        const val VOLUME_SEPARATOR = ':'
        val ENCODING: String = Charsets.UTF_8.name()
    }
}
