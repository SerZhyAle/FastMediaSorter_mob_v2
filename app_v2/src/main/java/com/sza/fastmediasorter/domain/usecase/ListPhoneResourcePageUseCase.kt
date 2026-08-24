package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.di.ApplicationScope
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * S1860: what an item asks before it decodes a picture - "is this page still willing to pay for one".
 *
 * A function rather than a holder class, so the page's remaining allowance stays a local of `page()`
 * and no item can read or reset it.
 */
private typealias ThumbnailGate = suspend (suspend () -> String?) -> String?

/**
 * S1697: resolves one paired-watch browse request into a bounded page of metadata the phone is
 * willing to expose. The watch never carries phone policy, so every visibility and permission
 * decision is taken here, per request, and a rejected request comes back as a protocol status
 * rather than as an exception the watch would have to interpret.
 */
class ListPhoneResourcePageUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val mediaScannerFactory: MediaScannerFactory,
    private val buildWatchThumbnail: BuildWatchThumbnailUseCase,
    // S1860: the scan runs here rather than in the caller's job, so a scanner blocked on a dead host
    // can be abandoned. See `withinScanBudget`.
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    suspend operator fun invoke(request: WearPhoneResourceRequest): WearPhoneResourcePage =
        when (request.kind) {
            WearPhoneResourceRequestKind.ROOT -> {
                if (request.isFlat == true || request.mediaType == FILTER_RECENTS) {
                    listFlatItems(request)
                } else {
                    listRoots(request)
                }
            }
            WearPhoneResourceRequestKind.CHILDREN -> listChildren(request)
            WearPhoneResourceRequestKind.OPEN -> failure(request, WearPhoneResourceResponseStatus.NOT_FOUND)
        }

    // S1911: the scan is a plugin boundary - any host, protocol or provider failure must become one
    // domain answer, so the broad arm is the contract rather than laziness. The one throwable that
    // must NOT be folded in, CancellationException, is rethrown as the first arm above it.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun listFlatItems(request: WearPhoneResourceRequest): WearPhoneResourcePage {
        val filter = request.mediaTypeFilter()
        val resources = runCatching { resourceRepository.getAllResourcesSync() }
            .getOrElse { error ->
                Timber.w(error, "Phone resource roots unavailable for flat list")
                return failure(request, WearPhoneResourceResponseStatus.PHONE_UNAVAILABLE)
            }
            .filter { it.isExposedToWatch() }
            .filter { it.holdsAnyOf(filter) }

        // S1860: one allowance for the whole walk, not one per resource - this list scans every
        // exposed resource in turn, so a single dead host must not spend the watch's whole wait.
        val deadline = scanDeadline()
        val allFiles = mutableListOf<Pair<MediaResource, MediaFile>>()
        for (resource in resources) {
            val scanner = runCatching { mediaScannerFactory.getScanner(resource.type) }.getOrNull()
            if (scanner != null) {
                val children = try {
                    withinScanBudget(deadline) {
                        scanner.listDirectoryContents(
                            path = resource.path,
                            supportedTypes = resource.supportedMediaTypes.narrowedBy(filter),
                            credentialsId = resource.credentialsId,
                            showHiddenFiles = resource.showHiddenFiles
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Phone resource flat listing failed")
                    null
                }
                if (children != null) {
                    val visibleFiles = children.visibleTo(resource).filter { !it.isDirectory }
                    for (file in visibleFiles) {
                        allFiles.add(resource to file)
                    }
                }
            }
        }

        val sorted = allFiles.sortedByDescending { pair ->
            pair.second.lastModified.coerceAtLeast(pair.second.createdDate)
        }
        return page(request, sorted) { (resource, file), gate ->
            file.toWireItem(PhoneResourceToken(resource.id, ""), gate)
        }
    }

    private suspend fun listRoots(request: WearPhoneResourceRequest): WearPhoneResourcePage {
        val visible = runCatching { resourceRepository.getAllResourcesSync() }
            .getOrElse { error ->
                Timber.w(error, "Phone resource roots unavailable")
                return failure(request, WearPhoneResourceResponseStatus.PHONE_UNAVAILABLE)
            }
            .filter { it.isExposedToWatch() }
            .filter { it.holdsAnyOf(request.mediaTypeFilter()) }

        return page(request, visible) { resource, _ -> resource.toRootItem() }
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

    // S1911: the scan is a plugin boundary - any host, protocol or provider failure must become one
    // domain answer, so the broad arm is the contract rather than laziness. The one throwable that
    // must NOT be folded in, CancellationException, is rethrown as the first arm above it.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun listChildrenOf(
        request: WearPhoneResourceRequest,
        parent: PhoneResourceToken,
        resource: MediaResource
    ): WearPhoneResourcePage {
        val scanner = runCatching { mediaScannerFactory.getScanner(resource.type) }.getOrNull()
        val deadline = scanDeadline()
        val children = scanner?.let { active ->
            try {
                withinScanBudget(deadline) {
                    active.listDirectoryContents(
                        path = parent.resolveAgainst(resource),
                        supportedTypes = resource.supportedMediaTypes.narrowedBy(request.mediaTypeFilter()),
                        credentialsId = resource.credentialsId,
                        showHiddenFiles = resource.showHiddenFiles
                    )
                }
            } catch (e: CancellationException) {
                // S1911: an abandoned request is not a failed scan. `runCatching` caught this too and
                // answered the watch SOURCE_UNAVAILABLE, which nothing could deliver anyway - the same
                // cancelled scope publishes the page - so it only mislabelled the log and the status.
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Phone resource listing failed")
                null
            }
        }

        return when {
            scanner == null -> failure(request, WearPhoneResourceResponseStatus.UNSUPPORTED_MEDIA)
            // S1697: the scan failed, not the link. The phone took this request and is answering it,
            // so blaming the watch connection would send the user to reconnect a phone already in hand.
            // S1860: a scan that outran its allowance lands here too, and for the same reason - the
            // source is what did not answer, and saying so beats the watch's silent ten-second give-up.
            children == null -> failure(request, WearPhoneResourceResponseStatus.SOURCE_UNAVAILABLE)
            else -> page(request, children.visibleTo(resource)) { file, gate ->
                file.toWireItem(parent, gate)
            }
        }
    }

    private fun List<MediaFile>.visibleTo(resource: MediaResource): List<MediaFile> =
        filter { resource.showHiddenFiles || !it.isHidden() }

    private suspend fun MediaFile.toWireItem(
        parent: PhoneResourceToken,
        gate: ThumbnailGate
    ): WearPhoneResourceItem =
        WearPhoneResourceItem(
            // A folder is always addressed by its path; a file prefers its MediaStore identity,
            // which is the only thing that separates two same-named files merged into one
            // virtual resource from different folders.
            token = PhoneResourceToken(
                resourceId = parent.resourceId,
                relativePath = parent.childPath(name),
                mediaStoreId = if (isDirectory) null else mediaStoreIdOrNull()
            ).serialize(),
            name = name,
            mimeType = if (isDirectory) null else type.toWireMimeType(),
            sizeBytes = size,
            isDirectory = isDirectory,
            // A folder carries no picture, and a file the use case declines carries none either -
            // both are ordinary states the watch draws as a type icon.
            thumbnailBase64 = if (isDirectory) {
                null
            } else {
                gate { buildWatchThumbnail(this@toWireItem) }
            }
        )

    /**
     * The trailing segment of a MediaStore `content://` URI is the row id. Null here is an ordinary
     * state, not a failure: the File-API scan fallback produces entries without a `contentUri`, and
     * those keep being addressed by their path.
     */
    private fun MediaFile.mediaStoreIdOrNull(): Long? =
        contentUri?.substringAfterLast('/')?.toLongOrNull()

    /**
     * The page window is applied here rather than by the scanner: a scanner page would count
     * entries the phone is about to hide, so the watch would receive short or empty pages while
     * `nextPageToken` still promised more.
     *
     * The wire item is built only for the window, never for the whole folder: since S1730 building
     * one costs a decode, and a folder of thousands would otherwise pay for thousands of pictures
     * to ship fifty.
     *
     * S1860: the same argument one level down. The window is fifty, but `MAX_PAGE_THUMBNAIL_CHARS`
     * over the per-item ceiling is about six pictures, so trimming the surplus after the decode
     * still paid for fifty of them - on a camera folder, tens of megabytes read and no answer at
     * all before the watch gave up at ten seconds. The gate below is consulted before each decode
     * instead, by size and by clock: one video frame can cost seconds while spending almost no
     * chars, so size alone would not have bounded the wait.
     */
    private suspend fun <T> page(
        request: WearPhoneResourceRequest,
        source: List<T>,
        toItem: suspend (T, ThumbnailGate) -> WearPhoneResourceItem
    ): WearPhoneResourcePage {
        val offset = request.pageToken?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val startedAtNanos = System.nanoTime()
        // The allowance is spent here, before the decode, rather than trimmed off the result after
        // it: this state is what tells an item it is not worth decoding at all.
        var spentChars = 0
        val expiresAtNanos = startedAtNanos + TimeUnit.MILLISECONDS.toNanos(THUMBNAIL_BUDGET_MS)
        val gate: ThumbnailGate = { decode ->
            val remainingChars = MAX_PAGE_THUMBNAIL_CHARS - spentChars
            val inTime = System.nanoTime() < expiresAtNanos
            val decoded = if (remainingChars > 0 && inTime) decode() else null
            val affordable = decoded?.takeIf { it.length <= remainingChars }
            spentChars += affordable?.length ?: 0
            affordable
        }
        val window = source.drop(offset).take(PAGE_SIZE).map { toItem(it, gate) }
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
            items = window,
            nextPageToken = if (nextOffset < source.size) nextOffset.toString() else null
        )
    }

    /**
     * S1860: the moment from which one request's scans stop being worth waiting for.
     *
     * The watch stops waiting after ten seconds and then reports the phone as unreachable. A refused
     * SFTP connect costs ten on its own and a dead SMB host three, so the phone has to give up first
     * and answer `SOURCE_UNAVAILABLE`, which names the thing the user can actually fix. One deadline
     * covers every scan a request makes, because the flat list walks each exposed resource in turn.
     */
    private fun scanDeadline(): Long =
        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SCAN_BUDGET_MS)

    /**
     * S1860: the scan is started OUTSIDE this call's job on purpose, and abandoned when it overruns.
     *
     * A timeout only ends a coroutine at a suspension point, and a scanner sitting in a blocking
     * socket connect never reaches one - so a plain `withTimeoutOrNull` around the scan waits for the
     * host to give up and is not a timeout at all. Measured on a paired run: a flat list over a
     * resource set holding dead SFTP hosts produced its page 11.2 s after the request, and the watch
     * had already declared the phone unreachable at 10. Abandoning the scan costs one thread until
     * the socket times out on its own; waiting for it costs the user the whole feature.
     */
    private suspend fun <T> withinScanBudget(deadlineNanos: Long, scan: suspend () -> T): T? {
        val remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime())
        if (remainingMs <= 0) {
            return null
        }
        val running = applicationScope.async { scan() }
        return withTimeoutOrNull(remainingMs) { running.await() }
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
    /**
     * S1846: the media kinds one watch chip asks for, or null when it asks for everything.
     *
     * The strings are the watch route's vocabulary rather than a second enum: the value travels from the
     * route through the request unchanged, so a mapping here is the only place the two spellings meet.
     * An unknown string is treated as no filter, because refusing the page would turn a future chip into
     * the dead end this ticket exists to remove.
     */
    private fun WearPhoneResourceRequest.mediaTypeFilter(): Set<MediaType>? = when (mediaType) {
        FILTER_PHOTOS -> setOf(MediaType.IMAGE, MediaType.GIF)
        FILTER_VIDEOS -> setOf(MediaType.VIDEO)
        FILTER_MUSIC -> setOf(MediaType.AUDIO)
        FILTER_DOCUMENTS -> setOf(MediaType.TEXT, MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT)
        FILTER_RECENTS -> setOf(
            MediaType.IMAGE,
            MediaType.GIF,
            MediaType.VIDEO,
            MediaType.AUDIO,
            MediaType.TEXT,
            MediaType.PDF,
            MediaType.EPUB,
            MediaType.OFFICE_DOCUMENT
        )
        else -> null
    }

    /** A resource is worth listing under a chip only when it is configured to hold that kind at all. */
    private fun MediaResource.holdsAnyOf(filter: Set<MediaType>?): Boolean =
        filter == null || supportedMediaTypes.any { it in filter }

    /**
     * The chip narrows the resource's own configuration and never widens it: a resource that was never
     * configured to show video must not start showing it because a watch asked for video.
     */
    private fun Set<MediaType>.narrowedBy(filter: Set<MediaType>?): Set<MediaType> =
        if (filter == null) this else intersect(filter)

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
        /** S1846: the watch route's media-kind vocabulary, mirrored from `WearRoutes.browsePhone`. */
        private const val FILTER_PHOTOS = "photos"
        private const val FILTER_VIDEOS = "videos"
        private const val FILTER_MUSIC = "music"
        private const val FILTER_DOCUMENTS = "documents"
        private const val FILTER_RECENTS = "recents"

        /** Upper bound of items in one watch-bound page. */
        const val PAGE_SIZE = 50

        /**
         * Upper bound of Base64 picture data one page may carry, across all its items.
         *
         * Sized well inside the Data Layer's per-message limit so the metadata the page also
         * carries still fits beside the pictures.
         *
         * S1893: the envelope now Base64-encodes the payload and the sender measures its final bytes.
         * A fifty-item page spends roughly 44 KB on names and tokens, so 24 KB of pictures stays
         * conservatively below the 100 KB GMS limit after Base64 expansion.
         */
        const val MAX_PAGE_THUMBNAIL_CHARS = 24 * 1024

        /**
         * S1860: how long one page may spend decoding pictures before the rest ship without one.
         *
         * Sized against the watch's ten-second wait together with `SCAN_BUDGET_MS`: four for the
         * scan, two for the pictures, and the rest is slack for the one decode that may start just
         * inside the allowance and overrun it. A decoder is blocking and does not answer
         * cancellation, so the allowance can only stop the NEXT decode, never the running one.
         */
        private const val THUMBNAIL_BUDGET_MS = 2_000L

        /** S1860: how long one request may spend scanning, across every resource it touches. */
        private const val SCAN_BUDGET_MS = 4_000L
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
    val relativePath: String,
    val mediaStoreId: Long? = null
) {

    fun serialize(): String =
        if (mediaStoreId == null) {
            "$resourceId$SEPARATOR$relativePath"
        } else {
            "$resourceId$SEPARATOR$MEDIA_ID_PREFIX$mediaStoreId"
        }

    fun childPath(name: String): String = if (relativePath.isEmpty()) name else "$relativePath/$name"

    fun resolveAgainst(resource: MediaResource): String =
        if (relativePath.isEmpty()) resource.path else "${resource.path.trimEnd('/')}/$relativePath"

    companion object {
        private const val SEPARATOR = ':'

        /**
         * Marks a token addressing its item by MediaStore id rather than by a path inside the
         * resource. An element of a virtual resource has no path within its resource - only a
         * MediaStore record - so resolving it by name would pick an arbitrary one of several
         * same-named files merged into that resource from different folders.
         */
        private const val MEDIA_ID_PREFIX = "media:"

        fun parse(raw: String): PhoneResourceToken? {
            val separator = raw.indexOf(SEPARATOR)
            val resourceId = if (separator > 0) raw.substring(0, separator).toLongOrNull() else null
            val payload = if (separator > 0) raw.substring(separator + 1) else ""
            // A traversal segment would let the watch address a folder the resource does not own.
            val escapesResource = payload.startsWith("/") || payload.split('/').contains("..")

            return when {
                resourceId == null -> null
                payload.startsWith(MEDIA_ID_PREFIX) ->
                    payload.removePrefix(MEDIA_ID_PREFIX).toLongOrNull()
                        ?.let { PhoneResourceToken(resourceId, "", it) }
                escapesResource -> null
                else -> PhoneResourceToken(resourceId, payload)
            }
        }
    }
}
