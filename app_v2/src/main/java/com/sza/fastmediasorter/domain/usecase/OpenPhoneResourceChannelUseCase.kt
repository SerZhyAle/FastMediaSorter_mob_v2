package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaExtensions
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.WearPhoneResourceRequest
import com.sza.fastmediasorter.domain.model.WearPhoneResourceResponseStatus
import com.sza.fastmediasorter.domain.repository.MediaStoreRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.util.VirtualPathUtils
import timber.log.Timber
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/**
 * S1697: the delivery half of the paired-phone contract. It answers one open request with either a
 * stream the bridge may pump, or a status the watch can explain to its user - never with an
 * exception, a path or a credential. Policy is decided here because the watch cannot be trusted to
 * enforce a limit it does not own.
 */
class OpenPhoneResourceChannelUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val mediaStoreRepository: MediaStoreRepository
) {

    suspend operator fun invoke(request: WearPhoneResourceRequest): PhoneResourceChannel {
        val item = request.itemToken?.let { PhoneResourceToken.parse(it) }
            ?: return PhoneResourceChannel.Rejected(WearPhoneResourceResponseStatus.NOT_FOUND)

        val lookup = runCatching { resourceRepository.getResourceById(item.resourceId) }
            .onFailure { Timber.w(it, "Phone resource lookup failed while opening a channel") }
        val resource = lookup.getOrNull()

        return when {
            lookup.isFailure -> PhoneResourceChannel.Rejected(WearPhoneResourceResponseStatus.PHONE_UNAVAILABLE)
            resource == null -> PhoneResourceChannel.Rejected(WearPhoneResourceResponseStatus.NOT_FOUND)
            !resource.isDeliverable() -> PhoneResourceChannel.Rejected(
                WearPhoneResourceResponseStatus.UNSUPPORTED_MEDIA
            )
            else -> approveOrReject(resource, item)
        }
    }

    private suspend fun approveOrReject(
        resource: MediaResource,
        item: PhoneResourceToken
    ): PhoneResourceChannel {
        val file = resolveFile(resource, item)
            ?: return PhoneResourceChannel.Rejected(WearPhoneResourceResponseStatus.NOT_FOUND)
        val mediaType = MediaExtensions.getMediaType(file.extension)
        val readable = runCatching { file.isFile && file.canRead() }.getOrDefault(false)

        return when {
            !readable -> PhoneResourceChannel.Rejected(WearPhoneResourceResponseStatus.NOT_FOUND)
            mediaType !in RENDERABLE_ON_WATCH ->
                PhoneResourceChannel.Rejected(WearPhoneResourceResponseStatus.UNSUPPORTED_MEDIA)
            file.length() > MAX_TRANSFER_BYTES ->
                PhoneResourceChannel.Rejected(WearPhoneResourceResponseStatus.TRANSFER_REJECTED)
            else -> PhoneResourceChannel.Approved(
                name = file.name,
                sizeBytes = file.length(),
                mediaType = mediaType,
                // A provider rather than an open stream: the bridge decides when the descriptor is
                // taken, so a request the watch abandons before the channel opens costs nothing.
                openStream = { file.inputStream() }
            )
        }
    }

    /**
     * Resolves the item the token names by the same authority that listed it. An identity token is
     * answered from MediaStore, because an element of a virtual resource has no path inside its
     * resource; a path token keeps the original lookup.
     */
    private suspend fun resolveFile(resource: MediaResource, item: PhoneResourceToken): File? =
        when {
            item.mediaStoreId != null -> resolveById(resource, item.mediaStoreId)
            // A virtual resource merges several folders, so its relative path is a bare name that
            // can address more than one file. Refusing beats handing over an arbitrary namesake.
            VirtualPathUtils.isVirtualPath(resource.path) -> null
            else -> File(item.resolveAgainst(resource))
        }

    /**
     * A MediaStore id addresses the whole device index rather than one resource, so an ordinary
     * folder confines the answer to itself here - otherwise an id would reach past the resource
     * and undo the containment `escapesResource` enforces on the path form. A virtual resource is
     * a device-wide aggregate with no path of its own, so there is nothing to confine it to.
     */
    private suspend fun resolveById(resource: MediaResource, mediaStoreId: Long): File? {
        val file = mediaStoreRepository.getFileByMediaStoreId(mediaStoreId)?.let { File(it.path) }
        return when {
            file == null -> null
            VirtualPathUtils.isVirtualPath(resource.path) -> file
            file.isInside(resource.path) -> file
            else -> null
        }
    }

    private fun File.isInside(root: String): Boolean = runCatching {
        canonicalPath.startsWith(File(root).canonicalPath + File.separator)
    }.getOrDefault(false)

    /**
     * Only phone-owned storage is delivered. A network resource is deliberately excluded: the watch
     * already reaches SMB, SFTP and FTP on its own, so relaying one through the phone would add a
     * second, slower path to content that is not the gap this ticket closes.
     */
    private fun MediaResource.isDeliverable(): Boolean =
        isAvailable && accessPin == null && type == ResourceType.LOCAL

    companion object {
        /** Upper bound of one watch-bound transfer. Beyond it the watch is told the transfer was rejected. */
        const val MAX_TRANSFER_BYTES: Long = 32L * 1024 * 1024

        /** Families the Wear app can actually render once the bytes arrive. */
        private val RENDERABLE_ON_WATCH = setOf(MediaType.IMAGE, MediaType.GIF, MediaType.VIDEO, MediaType.AUDIO)
    }
}

/** Outcome of one paired-phone open request. */
sealed interface PhoneResourceChannel {

    /**
     * An accepted transfer. [openStream] is opened by the bridge and closed by it; nothing here
     * holds the file open, so an abandoned request leaks no descriptor.
     */
    data class Approved(
        val name: String,
        val sizeBytes: Long,
        val mediaType: MediaType,
        val openStream: () -> InputStream
    ) : PhoneResourceChannel

    /** A refusal the watch can explain without knowing why the phone said no. */
    data class Rejected(val status: WearPhoneResourceResponseStatus) : PhoneResourceChannel
}
