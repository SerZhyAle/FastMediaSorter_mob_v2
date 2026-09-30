package com.sza.fastmediasorter.data.link.streaming

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.dash.DashSegmentIndex
import androidx.media3.exoplayer.dash.DashUtil
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.RangedUri
import androidx.media3.exoplayer.dash.manifest.Representation
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import androidx.media3.exoplayer.upstream.ParsingLoadable
import com.sza.fastmediasorter.domain.model.link.MediaQualityPreference
import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import java.io.File

/**
 * Picks the one stream set a link download keeps, then rebuilds it from the Media3 cache in playlist order.
 *
 * The cache directory is Media3's private layout: it also holds the cached playlists, the cache uid file
 * and segments split over several span files, and without stream keys the downloader fetches every
 * variant. Walking that directory fed the remuxer text files and mixed resolutions, so the order comes
 * from the playlist and the bytes come back through [cacheOnlySource], which reads by cache key.
 */
@UnstableApi
internal class CachedStreamAssembler(
    private val networkSource: DataSource,
    private val cacheOnlySource: DataSource,
) {

    /** Loads the root manifest through [networkSource], which also leaves it cached for the downloader. */
    fun select(manifest: StreamingManifest, quality: MediaQualityPreference): StreamSelection {
        val uri = Uri.parse(manifest.manifestUrl)
        return when (manifest) {
            is StreamingManifest.Hls -> selectHls(uri, quality)
            is StreamingManifest.Dash -> selectDash(uri, quality)
        }
    }

    /** Writes one file per selected stream into [outDir], init segment first; call after the download. */
    fun assemble(selection: StreamSelection, outDir: File): List<File> {
        outDir.mkdirs()
        val streams = when (selection) {
            is StreamSelection.Hls -> selection.mediaPlaylistUris.map(::hlsSegmentSpecs)
            is StreamSelection.Dash -> selection.tracks.map { dashSegmentSpecs(selection.manifest, it) }
        }
        return streams.mapIndexed { index, specs -> writeStream(File(outDir, "stream-$index.media"), specs) }
    }

    private fun selectHls(uri: Uri, quality: MediaQualityPreference): StreamSelection.Hls {
        val root = ParsingLoadable.load(networkSource, HlsPlaylistParser(), uri, C.DATA_TYPE_MANIFEST)
        if (root !is HlsMultivariantPlaylist || root.variants.isEmpty()) {
            return StreamSelection.Hls(listOf(uri), emptyList())
        }
        val variantIndex = pickVideoIndex(root.variants.map { it.format }, quality.maxResolutionPx)
        val variant = root.variants[variantIndex]
        // A rendition without a URI is muxed into the variant itself; only a separate one is a second stream.
        val audioIndex = root.audios.indexOfFirst { it.groupId == variant.audioGroupId && it.url != null }
        val audioUri: Uri? = root.audios.getOrNull(audioIndex)?.url
        val keepVideo = !(quality.audioOnly && audioUri != null)
        val keys = buildList {
            if (keepVideo) add(StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, variantIndex))
            if (audioUri != null) add(StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_AUDIO, audioIndex))
        }
        val uris = buildList {
            if (keepVideo) add(variant.url)
            if (audioUri != null) add(audioUri)
        }
        return StreamSelection.Hls(uris, keys)
    }

    private fun selectDash(uri: Uri, quality: MediaQualityPreference): StreamSelection.Dash {
        val manifest = DashUtil.loadManifest(networkSource, uri)
        val tracks = (0 until manifest.periodCount).flatMap { selectDashPeriod(manifest, it, quality) }
        return StreamSelection.Dash(manifest, tracks, tracks.map { it.streamKey })
    }

    private fun selectDashPeriod(
        manifest: DashManifest,
        periodIndex: Int,
        quality: MediaQualityPreference,
    ): List<DashTrack> {
        val sets = manifest.getPeriod(periodIndex).adaptationSets
        val videoSet = sets.indexOfFirst { it.type == C.TRACK_TYPE_VIDEO && it.representations.isNotEmpty() }
        val audioSet = sets.indexOfFirst { it.type == C.TRACK_TYPE_AUDIO && it.representations.isNotEmpty() }
        return buildList {
            if (videoSet >= 0 && !(quality.audioOnly && audioSet >= 0)) {
                val formats = sets[videoSet].representations.map { it.format }
                val pick = pickVideoIndex(formats, quality.maxResolutionPx)
                add(DashTrack(periodIndex, videoSet, pick, C.TRACK_TYPE_VIDEO))
            }
            if (audioSet >= 0) {
                val formats = sets[audioSet].representations.map { it.format }
                val pick = formats.indices.maxByOrNull { formats[it].bitrate } ?: 0
                add(DashTrack(periodIndex, audioSet, pick, C.TRACK_TYPE_AUDIO))
            }
        }
    }

    private fun hlsSegmentSpecs(playlistUri: Uri): List<DataSpec> {
        val playlist = ParsingLoadable.load(cacheOnlySource, HlsPlaylistParser(), playlistUri, C.DATA_TYPE_MANIFEST)
        if (playlist !is HlsMediaPlaylist) {
            throw StreamingDownloadException("not a media playlist: $playlistUri")
        }
        var lastInit: Pair<Uri, Long>? = null
        return buildList {
            for (segment in playlist.segments) {
                if (segment.hasGapTag) continue
                // fMP4 media segments carry no moov; the init segment is written again only when it changes.
                segment.initializationSegment?.let { init ->
                    val spec = hlsSpec(playlist.baseUri, init)
                    val identity = spec.uri to spec.position
                    if (identity != lastInit) {
                        add(spec)
                        lastInit = identity
                    }
                }
                add(hlsSpec(playlist.baseUri, segment))
            }
        }
    }

    private fun hlsSpec(baseUri: String, segment: HlsMediaPlaylist.SegmentBase): DataSpec =
        DataSpec(UriUtil.resolveToUri(baseUri, segment.url), segment.byteRangeOffset, segment.byteRangeLength)

    private fun dashSegmentSpecs(manifest: DashManifest, track: DashTrack): List<DataSpec> {
        val representation = manifest.getPeriod(track.periodIndex)
            .adaptationSets[track.adaptationSetIndex]
            .representations[track.representationIndex]
        val ranges = buildList {
            representation.initializationUri?.let(::add)
            addAll(dashMediaRanges(representation, track.trackType, manifest.getPeriodDurationUs(track.periodIndex)))
        }
        val baseUrl = representation.baseUrls[0].url
        return ranges.map { DashUtil.buildDataSpec(representation, baseUrl, it, 0, emptyMap()) }
    }

    private fun dashMediaRanges(
        representation: Representation,
        trackType: Int,
        periodDurationUs: Long,
    ): List<RangedUri> {
        val index = representation.index
        if (index == null) {
            // A single-file representation: the downloader fetched the chunk ranges its sidx names.
            val chunks = DashUtil.loadChunkIndex(cacheOnlySource, trackType, representation)
                ?: throw StreamingDownloadException("no cached segment index for ${representation.format.id}")
            return List(chunks.length) { RangedUri(null, chunks.offsets[it], chunks.sizes[it].toLong()) }
        }
        val count = index.getSegmentCount(periodDurationUs)
        if (count == DashSegmentIndex.INDEX_UNBOUNDED.toLong()) {
            throw StreamingDownloadException("live DASH representation has no bounded segment list")
        }
        val first = index.firstSegmentNum
        return (first until first + count).map { index.getSegmentUrl(it) }
    }

    private fun writeStream(target: File, specs: List<DataSpec>): File {
        target.outputStream().buffered().use { out ->
            for (spec in specs) {
                DataSourceInputStream(cacheOnlySource, spec).use { it.copyTo(out) }
            }
        }
        return target
    }
}

/**
 * Index of the rendition to keep: the tallest one within [maxHeightPx], else the smallest one.
 * Without any known height the highest bitrate wins, because nothing else distinguishes them.
 */
internal fun pickVideoIndex(formats: List<Format>, maxHeightPx: Int): Int {
    val sized = formats.indices.filter { formats[it].height != Format.NO_VALUE }
    if (sized.isEmpty()) return formats.indices.maxByOrNull { formats[it].bitrate } ?: 0
    val order = compareBy<Int>({ formats[it].height }, { formats[it].bitrate })
    val fitting = sized.filter { formats[it].height <= maxHeightPx }
    return if (fitting.isNotEmpty()) fitting.maxWith(order) else sized.minWith(order)
}

@UnstableApi
internal sealed interface StreamSelection {
    val streamKeys: List<StreamKey>

    data class Hls(
        val mediaPlaylistUris: List<Uri>,
        override val streamKeys: List<StreamKey>,
    ) : StreamSelection

    data class Dash(
        val manifest: DashManifest,
        val tracks: List<DashTrack>,
        override val streamKeys: List<StreamKey>,
    ) : StreamSelection
}

internal data class DashTrack(
    val periodIndex: Int,
    val adaptationSetIndex: Int,
    val representationIndex: Int,
    val trackType: Int,
) {
    val streamKey: StreamKey get() = StreamKey(periodIndex, adaptationSetIndex, representationIndex)
}
