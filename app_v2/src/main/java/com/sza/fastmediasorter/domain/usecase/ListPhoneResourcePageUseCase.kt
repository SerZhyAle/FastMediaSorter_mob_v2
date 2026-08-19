package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.WearPhoneResourceItem
import com.sza.fastmediasorter.domain.model.WearPhoneResourcePage
import com.sza.fastmediasorter.domain.model.WearPhoneResourceRequest
import com.sza.fastmediasorter.domain.model.WearPhoneResourceRequestKind
import com.sza.fastmediasorter.domain.model.WearPhoneResourceResponseStatus
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * S1697: resolves one paired-watch browse request into a bounded page of metadata the phone is
 * willing to expose. The watch never carries phone policy, so every visibility and permission
 * decision is taken here, per request, and a rejected request comes back as a protocol status
 * rather than as an exception the watch would have to interpret.
 */
class ListPhoneResourcePageUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val mediaScannerFactory: MediaScannerFactory,
    private val buildWatchThumbnail: BuildWatchThumbnailUseCase
) {

    suspend operator fun invoke(request: WearPhoneResourceRequest): WearPhoneResourcePage =
        when (request.kind) {
            WearPhoneResourceRequestKind.ROOT -> listRoots(request)
            WearPhoneResourceRequestKind.CHILDREN -> listChildren(request)
            WearPhoneResourceRequestKind.OPEN -> failure(request, WearPhoneResourceResponseStatus.NOT_FOUND)
        }

    private suspend fun listRoots(request: WearPhoneResourceRequest): WearPhoneResourcePage {
        val visible = runCatching { resourceRepository.getAllResourcesSync() }
            .getOrElse { error ->
                Timber.w(error, "Phone resource roots unavailable")
                return failure(request, WearPhoneResourceResponseStatus.PHONE_UNAVAILABLE)
            }
            .filter { it.isExposedToWatch() }

        return page(request, visible) { it.toRootItem() }
    }

    private suspend fun listChildren(request: WearPhoneResourceRequest): WearPhoneResourcePage {
        val parent = request.parentToken?.let { PhoneResourceToken.parse(it) }
            ?: return failure(request, WearPhoneResourceResponseStatus.NOT_FOUND)

        val lookup = runCatching { resourceRepository.getResourceById(parent.resourceId) }
            .onFailure { Timber.w(it, "Phone resource lookup failed") }
        val resource = lookup.getOrNull()

        return when {
            lookup.isFailure -> failure(request, WearPhoneResourceResponseStatus.PHONE_UNAVAILABLE)
            resource == null -> failure(request, WearPhoneResourceResponseStatus.NOT_FOUND)
            !resource.isExposedToWatch() -> failure(request, WearPhoneResourceResponseStatus.ACCESS_DENIED)
            else -> listChildrenOf(request, parent, resource)
        }
    }

    private suspend fun listChildrenOf(
        request: WearPhoneResourceRequest,
        parent: PhoneResourceToken,
        resource: MediaResource
    ): WearPhoneResourcePage {
        val scanner = runCatching { mediaScannerFactory.getScanner(resource.type) }.getOrNull()
        val children = scanner?.let { active ->
            runCatching {
                active.listDirectoryContents(
                    path = parent.resolveAgainst(resource),
                    supportedTypes = resource.supportedMediaTypes,
                    credentialsId = resource.credentialsId,
                    showHiddenFiles = resource.showHiddenFiles
                )
            }.onFailure { Timber.w(it, "Phone resource listing failed") }.getOrNull()
        }

        return when {
            scanner == null -> failure(request, WearPhoneResourceResponseStatus.UNSUPPORTED_MEDIA)
            children == null -> failure(request, WearPhoneResourceResponseStatus.PHONE_UNAVAILABLE)
            else -> page(request, children.visibleTo(resource)) { it.toWireItem(parent) }
        }
    }

    private fun List<MediaFile>.visibleTo(resource: MediaResource): List<MediaFile> =
        filter { resource.showHiddenFiles || !it.isHidden() }

    private suspend fun MediaFile.toWireItem(parent: PhoneResourceToken): WearPhoneResourceItem =
        WearPhoneResourceItem(
            token = PhoneResourceToken(parent.resourceId, parent.childPath(name)).serialize(),
            name = name,
            mimeType = if (isDirectory) null else type.toWireMimeType(),
            sizeBytes = size,
            isDirectory = isDirectory,
            // A folder carries no picture, and a file the use case declines carries none either -
            // both are ordinary states the watch draws as a type icon.
            thumbnailBase64 = if (isDirectory) null else buildWatchThumbnail(this)
        )

    /**
     * The page window is applied here rather than by the scanner: a scanner page would count
     * entries the phone is about to hide, so the watch would receive short or empty pages while
     * `nextPageToken` still promised more.
     *
     * The wire item is built only for the window, never for the whole folder: since S1730 building
     * one costs a decode, and a folder of thousands would otherwise pay for thousands of pictures
     * to ship fifty.
     */
    private suspend fun <T> page(
        request: WearPhoneResourceRequest,
        source: List<T>,
        toItem: suspend (T) -> WearPhoneResourceItem
    ): WearPhoneResourcePage {
        val offset = request.pageToken?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val window = source.drop(offset).take(PAGE_SIZE).map { toItem(it) }
        val nextOffset = offset + window.size
        // EMPTY describes the folder, not the window: a page past the last item is still a valid OK
        // answer about a folder that does have content.
        val status = if (source.isEmpty()) {
            WearPhoneResourceResponseStatus.EMPTY
        } else {
            WearPhoneResourceResponseStatus.OK
        }

        return WearPhoneResourcePage(
            requestId = request.requestId,
            status = status,
            items = withinPageBudget(window),
            nextPageToken = if (nextOffset < source.size) nextOffset.toString() else null
        )
    }

    /**
     * Caps what one page spends on pictures.
     *
     * The per-item ceiling alone does not bound the page: fifty items at that ceiling would exceed
     * what one Data Layer message carries, and the page would fail to arrive at all rather than
     * arrive with fewer pictures. Items past the budget keep every other field and simply carry no
     * picture - the state the watch already draws as a type icon.
     */
    private fun withinPageBudget(items: List<WearPhoneResourceItem>): List<WearPhoneResourceItem> {
        var spent = 0
        return items.map { item ->
            val cost = item.thumbnailBase64?.length ?: 0
            when {
                cost == 0 -> item
                spent + cost <= MAX_PAGE_THUMBNAIL_CHARS -> item.also { spent += cost }
                else -> item.copy(thumbnailBase64 = null)
            }
        }
    }

    private fun failure(
        request: WearPhoneResourceRequest,
        status: WearPhoneResourceResponseStatus
    ): WearPhoneResourcePage = WearPhoneResourcePage(requestId = request.requestId, status = status)

    /**
     * A PIN-protected or unavailable resource stays invisible instead of returning ACCESS_DENIED per
     * item: the watch has no way to satisfy the PIN, and naming a protected resource already tells
     * the holder of the watch that it exists. Streams are excluded because they are not scannable.
     */
    private fun MediaResource.isExposedToWatch(): Boolean =
        isAvailable &&
            accessPin == null &&
            type != ResourceType.HTTP_STREAM &&
            type != ResourceType.RTSP_STREAM

    private fun MediaResource.toRootItem(): WearPhoneResourceItem = WearPhoneResourceItem(
        token = PhoneResourceToken(id, "").serialize(),
        name = name,
        isDirectory = true
    )

    private fun MediaFile.isHidden(): Boolean = attributes?.hidden == true || name.startsWith(".")

    /**
     * The watch decides playability from a coarse family, not from a container-exact type: it hosts
     * fewer decoders than the phone, and a precise subtype would invite it to try a stream it cannot
     * render. Anything outside the three renderable families stays null, which the watch reads as
     * "metadata only".
     */
    private fun MediaType.toWireMimeType(): String? = when (this) {
        MediaType.IMAGE, MediaType.GIF -> "image/*"
        MediaType.VIDEO -> "video/*"
        MediaType.AUDIO -> "audio/*"
        else -> null
    }

    companion object {
        /** Upper bound of items in one watch-bound page. */
        const val PAGE_SIZE = 50

        /**
         * Upper bound of Base64 picture data one page may carry, across all its items.
         *
         * Sized well inside the Data Layer's per-message limit so the metadata the page also
         * carries still fits beside the pictures.
         */
        const val MAX_PAGE_THUMBNAIL_CHARS = 64 * 1024
    }
}

/**
 * Opaque-to-the-watch handle for one browsable node: a resource id plus a path relative to that
 * resource root. It deliberately carries no absolute phone path - the watch can neither learn where
 * the resource lives on the phone nor address anything outside it, because the path is re-anchored
 * to the resource root on every request. Being stateless also keeps a token valid across a restart
 * of the phone-side listener service, which a server-held token table would not.
 */
internal data class PhoneResourceToken(
    val resourceId: Long,
    val relativePath: String
) {

    fun serialize(): String = "$resourceId$SEPARATOR$relativePath"

    fun childPath(name: String): String = if (relativePath.isEmpty()) name else "$relativePath/$name"

    fun resolveAgainst(resource: MediaResource): String =
        if (relativePath.isEmpty()) resource.path else "${resource.path.trimEnd('/')}/$relativePath"

    companion object {
        private const val SEPARATOR = ':'

        fun parse(raw: String): PhoneResourceToken? {
            val separator = raw.indexOf(SEPARATOR)
            val resourceId = if (separator > 0) raw.substring(0, separator).toLongOrNull() else null
            val relativePath = if (separator > 0) raw.substring(separator + 1) else ""
            // A traversal segment would let the watch address a folder the resource does not own.
            val escapesResource = relativePath.startsWith("/") || relativePath.split('/').contains("..")

            return if (resourceId == null || escapesResource) {
                null
            } else {
                PhoneResourceToken(resourceId, relativePath)
            }
        }
    }
}
