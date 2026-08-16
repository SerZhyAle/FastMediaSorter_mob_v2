package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaExtensions
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.WearPhoneResourceRequest
import com.sza.fastmediasorter.domain.model.WearPhoneResourceResponseStatus
import com.sza.fastmediasorter.domain.repository.ResourceRepository
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
    private val resourceRepository: ResourceRepository
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

    private fun approveOrReject(resource: MediaResource, item: PhoneResourceToken): PhoneResourceChannel {
        val file = File(item.resolveAgainst(resource))
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
