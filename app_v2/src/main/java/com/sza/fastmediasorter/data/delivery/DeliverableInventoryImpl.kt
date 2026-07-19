package com.sza.fastmediasorter.data.delivery

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableCapability
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableInventory
import com.sza.fastmediasorter.domain.delivery.DeliverableDownloadRunner
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetDownloader
import com.sza.fastmediasorter.domain.delivery.DeliverableSourceDescriptor
import com.sza.fastmediasorter.domain.delivery.DownloadProgress
import com.sza.fastmediasorter.domain.delivery.ExtensionItem
import com.sza.fastmediasorter.domain.delivery.ExtensionSection
import com.sza.fastmediasorter.domain.delivery.ExtensionStatus
import com.sza.fastmediasorter.domain.usecase.streams.ImportStreamCatalogUseCase
import com.sza.fastmediasorter.ui.player.helpers.TesseractModelManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates the deliverable modules (sets A/B/C/D) and OCR language data into a single inventory
 * for the Extensions Manager screen (S0386 Phase 08). Module state comes from
 * [DeliverableCapabilityRepository], language state from [TesseractModelManager]; download sizes are
 * read from the contributed [DeliverableSourceDescriptor] map when a flavor ships one, else from a
 * pinned estimate (the map is empty until the Phase 05 de-bundle lands).
 *
 * The inventory is flavor-scoped (S0401): a row is emitted only when its set is offered in the
 * running flavor, so lite/photos (no OCR/translation capability, no media-playback descriptor) never
 * show rows that could not install there. OCR rows additionally honour the device-runtime axis: a
 * device that cannot run OCR (DeviceCapabilities) never sees OCR downloads it could not use.
 */
@Singleton
class DeliverableInventoryImpl @Inject constructor(
    private val runner: DeliverableDownloadRunner,
    private val downloader: DeliverableSetDownloader,
    private val repository: DeliverableCapabilityRepository,
    private val tesseractModelManager: TesseractModelManager,
    private val capabilityAvailability: CapabilityAvailability,
    private val importStreamCatalogUseCase: ImportStreamCatalogUseCase,
    private val bundled: BundledDeliverableSets,
    private val descriptors: Map<DeliverableSet, @JvmSuppressWildcards DeliverableSourceDescriptor>,
    @ApplicationContext private val appContext: Context
) : DeliverableInventory {

    // One status flow per item id; shared between the UI status flow and the download driver so
    // progress emitted during a download is observed by the list row.
    private val activeDownloads = ConcurrentHashMap<String, MutableStateFlow<ExtensionStatus>>()

    // Ordered + sectioned for the grouped screen (S0386 Phase 11/12): OCR (engines + OCR languages),
    // then Translation (module), then Media Playback (audio-viz + FFmpeg DTS). Each row is gated on
    // whether its set is offered in the running flavor (S0401): lite/photos compile in no OCR/
    // translation capability and contribute no media-playback descriptor, so those rows are dropped
    // rather than shown as non-installable.
    override fun getExtensions(): List<ExtensionItem> = buildList {
        if (isOcrOffered()) {
            add(
                ExtensionItem.Module(
                    id = moduleKey(DeliverableSet.OCR_ENGINES),
                    set = DeliverableSet.OCR_ENGINES,
                    displayNameRes = R.string.ext_ocr_engines_title,
                    descriptionRes = R.string.ext_ocr_engines_desc,
                    sizeLabel = moduleSizeLabel(DeliverableSet.OCR_ENGINES),
                    section = ExtensionSection.OCR,
                    statusFlow = moduleStatusFlow(DeliverableSet.OCR_ENGINES)
                )
            )
            // OCR language data is unusable without the OCR engine, so it follows the engine row.
            add(
                ExtensionItem.LanguageData(
                    id = languageKey("rus"),
                    languageCode = "rus",
                    displayNameRes = R.string.ext_lang_rus_title,
                    descriptionRes = R.string.ext_lang_rus_desc,
                    sizeLabel = formatBytes(LANG_SIZE_RUS),
                    section = ExtensionSection.OCR,
                    statusFlow = languageStatusFlow("rus")
                )
            )
            add(
                ExtensionItem.LanguageData(
                    id = languageKey("ukr"),
                    languageCode = "ukr",
                    displayNameRes = R.string.ext_lang_ukr_title,
                    descriptionRes = R.string.ext_lang_ukr_desc,
                    sizeLabel = formatBytes(LANG_SIZE_UKR),
                    section = ExtensionSection.OCR,
                    statusFlow = languageStatusFlow("ukr")
                )
            )
        }
        if (capabilityAvailability.isTranslationAvailable()) {
            add(
                ExtensionItem.Module(
                    id = moduleKey(DeliverableSet.TRANSLATION),
                    set = DeliverableSet.TRANSLATION,
                    displayNameRes = R.string.ext_translation_title,
                    descriptionRes = R.string.ext_translation_desc,
                    sizeLabel = moduleSizeLabel(DeliverableSet.TRANSLATION),
                    section = ExtensionSection.TRANSLATION,
                    statusFlow = moduleStatusFlow(DeliverableSet.TRANSLATION)
                )
            )
        }
        if (isSetOffered(DeliverableSet.AUDIO_VISUALIZATIONS)) {
            add(
                ExtensionItem.Module(
                    id = moduleKey(DeliverableSet.AUDIO_VISUALIZATIONS),
                    set = DeliverableSet.AUDIO_VISUALIZATIONS,
                    displayNameRes = R.string.ext_audio_viz_title,
                    descriptionRes = R.string.ext_audio_viz_desc,
                    sizeLabel = moduleSizeLabel(DeliverableSet.AUDIO_VISUALIZATIONS),
                    section = ExtensionSection.MEDIA_PLAYBACK,
                    statusFlow = moduleStatusFlow(DeliverableSet.AUDIO_VISUALIZATIONS)
                )
            )
        }
        if (isSetOffered(DeliverableSet.FFMPEG_DTS)) {
            add(
                ExtensionItem.Module(
                    id = moduleKey(DeliverableSet.FFMPEG_DTS),
                    set = DeliverableSet.FFMPEG_DTS,
                    displayNameRes = R.string.ext_ffmpeg_dts_title,
                    descriptionRes = R.string.ext_ffmpeg_dts_desc,
                    sizeLabel = moduleSizeLabel(DeliverableSet.FFMPEG_DTS),
                    section = ExtensionSection.MEDIA_PLAYBACK,
                    statusFlow = moduleStatusFlow(DeliverableSet.FFMPEG_DTS)
                )
            )
        }
        if (capabilityAvailability.isStreamsAvailable()) {
            Timber.d("S1110: stream catalog size label=%s", formatBytes(STREAM_CATALOG_SIZE))
            add(
                ExtensionItem.Catalog(
                    id = STREAM_CATALOG_ID,
                    displayNameRes = R.string.ext_streams_title,
                    descriptionRes = R.string.ext_streams_desc,
                    sizeLabel = formatBytes(STREAM_CATALOG_SIZE),
                    section = ExtensionSection.STREAMS,
                    statusFlow = catalogStatusFlow(STREAM_CATALOG_ID)
                )
            )
        }
    }

    // OCR_ENGINES + TRANSLATION are gated by compile-time capability (the .so / DFM may be delivered
    // on demand with no descriptor at this point), while data-payload sets (AUDIO_VISUALIZATIONS) and
    // the DTS decoder are gated by whether the flavor contributes a descriptor or bundles the set -
    // lite/photos contribute neither.
    // OCR additionally folds in the device-runtime axis: a device that cannot run OCR (API too old or
    // RAM too low, see DeviceCapabilities) must not be offered OCR downloads it could never use, so the
    // engine + OCR language rows are dropped there even on an OCR-compiled flavor.
    private fun isOcrOffered(): Boolean = capabilityAvailability.isOcrAvailable(appContext)

    private fun isSetOffered(set: DeliverableSet): Boolean =
        descriptors.containsKey(set) || bundled.contains(set)

    override fun download(item: ExtensionItem): Flow<DownloadProgress> {
        val statusFlow = statusFlowFor(item)
        return flow {
            val progressFlow = when (item) {
                is ExtensionItem.Module -> {
                    runner.enqueue(item.set)
                    runner.progressOf(item.set)
                }
                is ExtensionItem.LanguageData -> downloadTesseractModel(item.languageCode)
                is ExtensionItem.Catalog -> importStreamCatalog()
            }
            progressFlow.collect { progress ->
                statusFlow.value = progress.toExtensionStatus()
                // Persist the installed marker on success so the capability flow re-emits and the row
                // flips to Installed even for a set whose downloader does not write the marker itself -
                // the translation Play dynamic-feature path (the file-set downloader already marks it;
                // re-marking is idempotent).
                if (progress == DownloadProgress.Installed && item is ExtensionItem.Module) {
                    repository.markInstalled(item.set)
                }
                emit(progress)
            }
        }
    }

    override suspend fun uninstall(item: ExtensionItem) {
        when (item) {
            is ExtensionItem.Module -> repository.uninstall(item.set)
            is ExtensionItem.LanguageData -> tesseractModelManager.deleteModel(item.languageCode)
            // S0575: catalog rows are re-importable; uninstall only resets the row status below and
            // never deletes manually-added stream sources (strategic decision: off keeps data).
            is ExtensionItem.Catalog -> Unit
        }
        statusFlowFor(item).value = ExtensionStatus.NotInstalled
    }

    // Merges the persisted capability state with any in-flight download so the row reflects both:
    // a live download (or its failure) wins, otherwise the repository's installed/not state shows.
    private fun moduleStatusFlow(set: DeliverableSet): Flow<ExtensionStatus> {
        // S0971: a bundled set ships inside the APK, so it is always installed - never surface a
        // (Play-forbidden) download for it. Short-circuit before the repository/marker state.
        if (bundled.contains(set)) return flowOf(ExtensionStatus.Installed)
        val active = activeDownloads.getOrPut(moduleKey(set)) {
            MutableStateFlow(
                if (repository.isInstalledBlocking(set)) ExtensionStatus.Installed
                else ExtensionStatus.NotInstalled
            )
        }
        return combine(repository.stateOf(set), active) { cap, download ->
            when {
                download is ExtensionStatus.Downloading || download is ExtensionStatus.Failed -> download
                cap == DeliverableCapability.INSTALLED -> ExtensionStatus.Installed
                else -> ExtensionStatus.NotInstalled
            }
        }.distinctUntilChanged()
    }

    private fun languageStatusFlow(languageCode: String): Flow<ExtensionStatus> =
        activeDownloads.getOrPut(languageKey(languageCode)) {
            MutableStateFlow(
                if (tesseractModelManager.isModelInstalled(languageCode)) ExtensionStatus.Installed
                else ExtensionStatus.NotInstalled
            )
        }

    private fun statusFlowFor(item: ExtensionItem): MutableStateFlow<ExtensionStatus> =
        activeDownloads.getOrPut(item.id) { MutableStateFlow(ExtensionStatus.NotInstalled) }

    private fun catalogStatusFlow(id: String): Flow<ExtensionStatus> =
        activeDownloads.getOrPut(id) { MutableStateFlow(ExtensionStatus.NotInstalled) }

    // S0575: the stream catalog is fetched directly (not a DeliverableSet), so map its one-shot import
    // result to the terminal DownloadProgress the shared row machinery already understands.
    private fun importStreamCatalog(): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Queued)
        emit(
            when (val result = importStreamCatalogUseCase()) {
                is ImportStreamCatalogUseCase.CatalogImportResult.Success -> DownloadProgress.Installed
                ImportStreamCatalogUseCase.CatalogImportResult.Empty -> DownloadProgress.Failed("empty")
                is ImportStreamCatalogUseCase.CatalogImportResult.Failure -> DownloadProgress.Failed(result.reason)
            }
        )
    }

    private fun downloadTesseractModel(languageCode: String): Flow<DownloadProgress> = channelFlow {
        send(DownloadProgress.Queued)
        val success = tesseractModelManager.downloadModel(languageCode) { percent, _, _ ->
            trySend(DownloadProgress.Running(percent, 0, 0))
        }
        send(if (success) DownloadProgress.Installed else DownloadProgress.Failed("Download or validation failed"))
    }

    private fun DownloadProgress.toExtensionStatus(): ExtensionStatus = when (this) {
        DownloadProgress.Queued -> ExtensionStatus.Downloading(0)
        is DownloadProgress.Running -> ExtensionStatus.Downloading(percent)
        DownloadProgress.Verifying -> ExtensionStatus.Downloading(99)
        DownloadProgress.Installed -> ExtensionStatus.Installed
        is DownloadProgress.Failed -> ExtensionStatus.Failed(reason)
    }

    private fun moduleSizeLabel(set: DeliverableSet): String {
        val fromDescriptor = descriptors[set]?.files?.sumOf { it.minSize } ?: 0L
        return formatBytes(if (fromDescriptor > 0L) fromDescriptor else FALLBACK_SIZE[set] ?: 0L)
    }

    // S1110: "%.0f MB" collapses any sub-MB value to "0 MB" (the growing, non-pinned stream catalog
    // hit this). Render < 1 MB in KB so no deliverable ever shows "0 MB"; whole MB for pinned modules.
    private fun formatBytes(bytes: Long): String {
        val megabytes = bytes / BYTES_PER_MB
        return if (megabytes < 1) {
            String.format(Locale.US, "%.0f KB", bytes / BYTES_PER_KB)
        } else {
            String.format(Locale.US, "%.0f MB", megabytes)
        }
    }

    private fun moduleKey(set: DeliverableSet): String = "set_${set.name.lowercase(Locale.ROOT)}"

    private fun languageKey(languageCode: String): String = "lang_ocr_$languageCode"

    companion object {
        // Estimated arm64-v8a download sizes (bytes) shown until a flavor contributes a real
        // descriptor; values mirror temp/S0386_B3_so_staging.md (strategic §5.4).
        private const val STREAM_CATALOG_ID = "stream_catalog"
        // S1110: approximate size of the growing, non-pinned stream-catalog.zip (measured 2.44 MB on
        // 2026-07-19: 2.31 MiB favicon atlas + 0.92 MB CSV). Not the stale S0386 staging estimate.
        private const val STREAM_CATALOG_SIZE = 2_500_000L
        private const val BYTES_PER_KB = 1024.0
        private const val BYTES_PER_MB = 1024.0 * 1024.0
        private const val LANG_SIZE_RUS = 15_000_000L
        private const val LANG_SIZE_UKR = 11_600_000L
        private val FALLBACK_SIZE = mapOf(
            DeliverableSet.OCR_ENGINES to 7_514_856L,
            DeliverableSet.TRANSLATION to 17_380_608L,
            DeliverableSet.AUDIO_VISUALIZATIONS to 6_100_000L,
            DeliverableSet.FFMPEG_DTS to 7_675_704L
        )
    }
}
