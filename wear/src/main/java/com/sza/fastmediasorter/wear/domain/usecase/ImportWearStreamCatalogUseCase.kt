package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.repository.WearFaviconAtlasStore
import com.sza.fastmediasorter.wear.data.repository.WearStreamCatalogCsvParser
import com.sza.fastmediasorter.wear.data.repository.WearStreamCollectionsJsonParser
import com.sza.fastmediasorter.wear.domain.model.CatalogImportResult
import com.sza.fastmediasorter.wear.domain.model.CatalogPayload
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamCollectionRepository
import com.sza.fastmediasorter.wear.util.warnUnlessCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * S1708: Download the curated stream catalog ZIP, parse `streams.csv`, extract the favicon sprite-atlas,
 * and save channels to [WearStreamChannelRepository] and atlas to [WearFaviconAtlasStore].
 * S2669: the same archive may carry `collections.json`, which is parsed after the channel merge and
 * stored whole through [WearStreamCollectionRepository].
 */
class ImportWearStreamCatalogUseCase @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val parser: WearStreamCatalogCsvParser,
    private val classifier: ClassifyWearStreamMediaKindUseCase,
    private val repository: WearStreamChannelRepository,
    private val faviconAtlasStore: WearFaviconAtlasStore,
    // S2669: the second payload of the same archive - curated collections, replaced whole per import.
    private val collectionsParser: WearStreamCollectionsJsonParser,
    private val collectionRepository: WearStreamCollectionRepository,
    private val requestWearTileRefreshUseCase: RequestWearTileRefreshUseCase
) {
    // Every step here talks to the network, a ZIP stream or the store, and each one already ends in a
    // logged CatalogImportResult rather than a rethrow. Narrowing to IOException would let an
    // IllegalStateException out of OkHttp or the zip reader and crash the watch on a bad archive,
    // which is the opposite of what a catalog refresh should do.
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(): CatalogImportResult = withContext(Dispatchers.IO) {
        Timber.i("Wear stream catalog import: starting")
        val payload = try {
            downloadCatalog()
        } catch (e: Exception) {
            e.warnUnlessCancellation("Wear stream catalog import failed: download/unzip")
            return@withContext CatalogImportResult.Failure(e.message ?: "download error")
        }

        if (payload == null) {
            Timber.w("Wear stream catalog import: no streams.csv entry found in archive")
            return@withContext CatalogImportResult.Empty
        }

        val entries = try {
            parser.parse(payload.csv)
        } catch (e: Exception) {
            e.warnUnlessCancellation("Wear stream catalog import failed: parse")
            return@withContext CatalogImportResult.Failure(e.message ?: "parse error")
        }

        if (entries.isEmpty()) return@withContext CatalogImportResult.Empty

        try {
            val coords = entries
                .filter { it.faviconIndex != null }
                .associate { it.url to it.faviconIndex!! }
            faviconAtlasStore.write(payload.atlasPng, coords)
            Timber.i(
                "Wear stream catalog favicons: atlas=%d bytes, indexed rows=%d/%d",
                payload.atlasPng?.size ?: 0,
                coords.size,
                entries.size
            )
        } catch (e: Exception) {
            e.warnUnlessCancellation("Wear stream catalog import: favicon sidecar write failed (rows still merge)")
        }

        val channels = entries.map { entry ->
            WearStreamChannel(
                id = UUID.randomUUID().toString(),
                name = entry.name,
                url = entry.url,
                mediaKind = entry.mediaKind.uppercase().ifBlank { classifier.classify(entry.url) },
                faviconIndex = entry.faviconIndex,
                category = entry.category.ifBlank { null },
                topic = entry.topic.ifBlank { null },
                language = entry.language.ifBlank { null },
                country = entry.country.ifBlank { null },
                access = entry.access.ifBlank { null }
            )
        }

        try {
            // S1799 ADR-1: the import owns only catalog content - rows transferred from the phone
            // survive the refresh, or a user-sent stream silently vanishes on the next update.
            val stored = repository.getAllChannels()
            val merged = mergePreservingPhoneRows(channels, stored)
            Timber.i(
                "Wear stream catalog import: preserved %d phone-origin channel(s)",
                merged.size - channels.size
            )
            repository.saveChannels(merged)
            requestWearTileRefreshUseCase(WearTileKind.STREAM)
        } catch (e: Exception) {
            e.warnUnlessCancellation("Wear stream catalog import failed: save")
            return@withContext CatalogImportResult.Failure(e.message ?: "save error")
        }

        // S2669: after the channel merge, because a membership row names a url that is only
        // meaningful once the bank carrying it is in place. Non-fatal by design, like the favicon
        // sidecar above it: a collections payload that fails to parse or to store must not cost the
        // wearer the bank that imported fine. A null payload means the archive carried no such
        // entry, and the stored collections stay ALONE rather than being cleared - an
        // already-released archive has no entry, and clearing on its absence would empty the picker
        // on every import from an older publication.
        if (payload.collectionsJson != null) {
            try {
                val collections = collectionsParser.parse(payload.collectionsJson)
                collectionRepository.saveAll(collections)
            } catch (e: Exception) {
                e.warnUnlessCancellation("Wear stream catalog import: collections could not be stored; channels unaffected")
            }
        }

        Timber.i("Wear stream catalog import done: %d channels saved", channels.size)
        CatalogImportResult.Success(channels.size)
    }

    private fun downloadCatalog(): CatalogPayload? {
        val request = Request.Builder().url(CATALOG_URL).build()
        val client = okHttpClient.newBuilder()
            .callTimeout(CATALOG_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val stream = response.body?.byteStream() ?: error("empty response body")
            return extractCatalog(stream)
        }
    }

    internal fun extractCatalog(stream: InputStream): CatalogPayload? {
        val csvByName = LinkedHashMap<String, String>()
        val contents = ZipInputStream(stream).use { zip -> readArchive(zip, csvByName) }
        // A catalog names its table streams.csv; any other .csv is accepted only as a fallback, so a
        // renamed table still imports instead of reporting an empty archive.
        val csv = csvByName.entries.firstOrNull { it.key.endsWith("streams.csv") }?.value
            ?: csvByName.values.firstOrNull()
            ?: return null
        return CatalogPayload(
            csv = csv,
            atlasPng = contents.atlasPng,
            collectionsJson = contents.collectionsJson
        )
    }

    /** What the single archive walk captured besides the CSV tables. */
    private class ArchiveContents(val atlasPng: ByteArray?, val collectionsJson: String?)

    private fun readArchive(zip: ZipInputStream, csvByName: MutableMap<String, String>): ArchiveContents {
        var atlasPng: ByteArray? = null
        var collectionsJson: String? = null
        var entry = zip.nextEntry
        while (entry != null) {
            val name = entry.name.lowercase()
            if (name.endsWith(COLLECTIONS_ENTRY)) {
                // S2669: matched before the .csv branch, and deliberately NOT named .csv - every
                // released watch build loads any .csv entry as a fallback stream bank, so a
                // .csv-named collections payload would be loaded as the bank itself the day the
                // real bank fails to parse. Its own cap, because a collections payload is neither
                // a bank nor an atlas; over-cap drops the collections and keeps the bank.
                collectionsJson = readCappedBytes(zip, MAX_COLLECTIONS_BYTES)
                    ?.toString(Charsets.UTF_8)
            } else if (name.endsWith(".csv")) {
                csvByName[name] = readCappedUtf8(zip)
            } else if (name.endsWith("favicon-atlas.png")) {
                atlasPng = readCappedBytes(zip, MAX_ATLAS_BYTES)
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        return ArchiveContents(atlasPng, collectionsJson)
    }

    /**
     * S1820: an over-cap CSV THROWS rather than returning null. Returning null collapsed into the same
     * Empty result as an archive holding no CSV at all, so the day the bank outgrew the cap every watch
     * would have reported "catalog is empty" with nothing in the log to separate the two. The throw is
     * caught by the invoke() download branch and reported as a Failure naming the cap. The atlas path
     * keeps the null tolerance on purpose - there over-cap means "drop the atlas, keep the CSV".
     */
    private fun readCappedUtf8(zip: ZipInputStream): String {
        val bytes = readCappedBytes(zip, MAX_CSV_BYTES)
            ?: error("catalog CSV exceeds the ${MAX_CSV_BYTES / BYTES_PER_MIB} MiB cap")
        return String(bytes, Charsets.UTF_8)
    }

    private fun readCappedBytes(zip: ZipInputStream, cap: Int): ByteArray? {
        val buffer = ByteArray(READ_BUFFER_BYTES)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = zip.read(buffer)
            if (read == -1) break
            total += read
            if (total > cap) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {

        /**
         * S1799: catalog import replaces catalog rows only. A stored row with
         * [WearStreamChannel.ORIGIN_PHONE] whose url the fresh catalog does not carry is kept;
         * one whose url has appeared in the catalog is superseded by the catalog row.
         */
        internal fun mergePreservingPhoneRows(
            catalog: List<WearStreamChannel>,
            stored: List<WearStreamChannel>
        ): List<WearStreamChannel> {
            val catalogUrls = catalog.mapTo(HashSet()) { it.url }
            val preserved = stored.filter {
                it.origin == WearStreamChannel.ORIGIN_PHONE && it.url !in catalogUrls
            }
            return catalog + preserved
        }

        private const val CATALOG_URL =
            "https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/stream-catalog.zip"
        private const val READ_BUFFER_BYTES = 8 * 1024
        private const val BYTES_PER_MIB = 1024 * 1024

        // S1820: raised from 8 MiB. Measured 2026-08-19, the published streams.csv is 5.83 MB across
        // 19534 rows, up from 2691 rows a month earlier - 1.4x headroom against sevenfold monthly
        // growth. The watch downloads the same archive as the phone, so both caps move together.
        private const val MAX_CSV_BYTES = 32 * BYTES_PER_MIB
        private const val MAX_ATLAS_BYTES = 30 * BYTES_PER_MIB

        // S2669: the collections payload names urls the bank already carries, so it is bounded by
        // the bank's own size rather than by an atlas's - the same cap and the same reasoning the
        // phone import uses, because both clients read the same archive.
        private const val MAX_COLLECTIONS_BYTES = 8 * BYTES_PER_MIB

        /** The archive entry name fixed by the delivery contract; deliberately not a `.csv`. */
        private const val COLLECTIONS_ENTRY = "collections.json"

        // S1820: raised from 30 s. The published zip is 7.31 MB, which 30 s demanded be pulled at
        // ~250 KB/s with no dip - and a watch usually pulls it over a proxied phone link, which is
        // slower than the phone's own. 180 s drops the required floor to ~42 KB/s. Finite on purpose:
        // a host that trickles bytes resets the read timeout on every chunk and would otherwise hold
        // the import coroutine forever.
        private const val CATALOG_CALL_TIMEOUT_SECONDS = 180L
    }
}
