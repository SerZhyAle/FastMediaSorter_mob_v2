package com.sza.fastmediasorter.data.link.nolegal

import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import com.sza.fastmediasorter.domain.usecase.link.MediaMimeWhitelist
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import com.sza.fastmediasorter.domain.usecase.link.SiteBatchItem
import com.sza.fastmediasorter.domain.usecase.link.UrlExtractionStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewPipeSiteExtractionStrategy @Inject constructor(
    private val downloader: NewPipeOkHttpDownloader,
    private val direct: DirectFileExtractionStrategy,
) : UrlExtractionStrategy {

    override val id: String = "site"

    override suspend fun probe(url: String): ProbeResult = withContext(Dispatchers.IO) {
        downloader.ensureInitialized()
        val service = resolveService(url) ?: return@withContext ProbeResult.NotApplicable
        when (runCatching { service.getLinkTypeByUrl(url) }.getOrNull()) {
            StreamingService.LinkType.STREAM,
            StreamingService.LinkType.PLAYLIST,
            -> ProbeResult.Applicable(tentativeMime = null, tentativeSizeBytes = null)
            else -> ProbeResult.NotApplicable
        }
    }

    override suspend fun open(
        url: String,
        onProgress: (bytesRead: Long, total: Long?) -> Unit,
    ): OpenResult = withContext(Dispatchers.IO) {
        downloader.ensureInitialized()
        val service = resolveService(url) ?: return@withContext OpenResult.NotFound("site_service_not_supported")
        val linkType = try {
            service.getLinkTypeByUrl(url)
        } catch (error: ParsingException) {
            Timber.w(error, "S0117: site link-type parse failed for %s", url)
            return@withContext OpenResult.NotFound("site_link_type_failed")
        }

        when (linkType) {
            StreamingService.LinkType.STREAM -> openStream(service, url, onProgress)
            StreamingService.LinkType.PLAYLIST -> openPlaylist(service, url)
            else -> OpenResult.NotFound("site_link_type_unsupported")
        }
    }

    private suspend fun openStream(
        service: StreamingService,
        url: String,
        onProgress: (bytesRead: Long, total: Long?) -> Unit,
    ): OpenResult = try {
        val info = StreamInfo.getInfo(service, url)
        val safeBaseName = sanitiseFileName(info.name)

        selectProgressiveStream(info)?.let { selection ->
            return when (val opened = direct.open(selection.url, onProgress)) {
                is OpenResult.Stream -> opened.copy(fileName = "$safeBaseName.${selection.extension}")
                else -> opened
            }
        }

        info.hlsUrl?.takeIf { it.isNotBlank() }?.let { manifestUrl ->
            return OpenResult.Streaming(
                manifest = StreamingManifest.Hls(manifestUrl = manifestUrl),
                tentativeFileName = "$safeBaseName.mp4",
            )
        }

        info.dashMpdUrl?.takeIf { it.isNotBlank() }?.let { manifestUrl ->
            return OpenResult.Streaming(
                manifest = StreamingManifest.Dash(manifestUrl = manifestUrl),
                tentativeFileName = "$safeBaseName.mp4",
            )
        }

        OpenResult.NotFound("site_no_supported_stream")
    } catch (error: Throwable) {
        mapExtractionFailure(error)
    }

    private suspend fun openPlaylist(service: StreamingService, url: String): OpenResult = try {
        val info = PlaylistInfo.getInfo(service, url)
        val items = info.relatedItems
            .mapNotNull { item ->
                item.url
                    .takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?.let { resolvedUrl -> SiteBatchItem(url = resolvedUrl, title = item.name) }
            }
            .distinctBy { it.url }
            .take(MAX_BATCH_ITEMS)

        if (items.isEmpty()) {
            OpenResult.NotFound("site_playlist_empty")
        } else {
            OpenResult.Batch(items = items, label = info.name)
        }
    } catch (error: Throwable) {
        mapExtractionFailure(error)
    }

    private fun resolveService(url: String): StreamingService? {
        val service = runCatching { NewPipe.getServiceByUrl(url) }.getOrElse { return null }
        return service.takeIf { it.serviceId in ALLOWED_SERVICE_IDS }
    }

    private fun selectProgressiveStream(info: StreamInfo): SelectedStream? {
        val preferredVideo = info.videoStreams
            .filter { stream ->
                val format = stream.format ?: return@filter false
                stream.isUrl &&
                    stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                    MediaMimeWhitelist.isAllowed(format.mimeType)
            }
            .sortedWith(
                compareByDescending<VideoStream> { it.format == MediaFormat.MPEG_4 }
                    .thenByDescending { it.height }
                    .thenByDescending { it.bitrate },
            )
            .firstOrNull()

        if (preferredVideo != null) {
            // S0175: Stream.url deprecated in v0.26.1 — use content (non-null) since isUrl is pre-checked by filter above.
            val url = preferredVideo.content.takeIf { it.isNotBlank() } ?: return null
            val format = preferredVideo.format ?: return null
            return SelectedStream(
                url = url,
                extension = format.suffix.ifBlank { "mp4" },
            )
        }

        val preferredAudio = info.audioStreams
            .filter { stream ->
                val format = stream.format ?: return@filter false
                stream.isUrl &&
                    stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                    MediaMimeWhitelist.isAllowed(format.mimeType)
            }
            .sortedWith(
                compareByDescending<AudioStream> {
                    it.format == MediaFormat.M4A || it.format == MediaFormat.MP3
                }.thenByDescending { it.averageBitrate },
            )
            .firstOrNull()

        if (preferredAudio != null) {
            // S0175: Stream.url deprecated in v0.26.1 — use content (non-null) since isUrl is pre-checked by filter above.
            val url = preferredAudio.content.takeIf { it.isNotBlank() } ?: return null
            val format = preferredAudio.format ?: return null
            return SelectedStream(
                url = url,
                extension = format.suffix.ifBlank { "m4a" },
            )
        }

        return null
    }

    private fun mapExtractionFailure(error: Throwable): OpenResult {
        return when (error) {
            is PrivateContentException,
            is PaidContentException,
            is AgeRestrictedContentException,
            is ReCaptchaException,
            -> OpenResult.Blocked(com.sza.fastmediasorter.domain.usecase.link.BlockedReason.AuthRequired)

            is ContentNotAvailableException,
            is GeographicRestrictionException,
            -> OpenResult.NotFound("site_content_unavailable")

            is ParsingException,
            is ExtractionException,
            -> OpenResult.NotFound("site_extraction_failed")

            is IOException -> OpenResult.Error(error)
            else -> OpenResult.Error(IOException(error.message ?: "site_extraction_failed", error))
        }
    }

    private fun sanitiseFileName(name: String?): String {
        return name
            ?.replace(Regex("[^a-zA-Z0-9_.\\-]"), "_")
            ?.trim('_')
            ?.take(120)
            ?.ifBlank { null }
            ?: "download_${System.currentTimeMillis()}"
    }

    private data class SelectedStream(
        val url: String,
        val extension: String,
    )

    private companion object {
        const val MAX_BATCH_ITEMS = 30

        // S0117: keep the supported service surface explicit and compile-time bounded.
        val ALLOWED_SERVICE_IDS = setOf(
            ServiceList.YouTube.serviceId,
            ServiceList.SoundCloud.serviceId,
            ServiceList.MediaCCC.serviceId,
            ServiceList.PeerTube.serviceId,
            ServiceList.Bandcamp.serviceId,
        )
    }
}