package com.sza.fastmediasorter.data.delivery

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.delivery.DeliverableCapability
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableInventory
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetDownloader
import com.sza.fastmediasorter.domain.delivery.DeliverableSourceDescriptor
import com.sza.fastmediasorter.domain.delivery.DownloadProgress
import com.sza.fastmediasorter.domain.delivery.ExtensionItem
import com.sza.fastmediasorter.domain.delivery.ExtensionSection
import com.sza.fastmediasorter.domain.delivery.ExtensionStatus
import com.sza.fastmediasorter.ui.player.helpers.TesseractModelManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
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
 */
@Singleton
class DeliverableInventoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: DeliverableSetDownloader,
    private val repository: DeliverableCapabilityRepository,
    private val descriptors: Map<DeliverableSet, @JvmSuppressWildcards DeliverableSourceDescriptor>
) : DeliverableInventory {

    private val tesseractModelManager = TesseractModelManager(context)

    // One status flow per item id; shared between the UI status flow and the download driver so
    // progress emitted during a download is observed by the list row.
    private val activeDownloads = ConcurrentHashMap<String, MutableStateFlow<ExtensionStatus>>()

    // Ordered + sectioned for the grouped screen (S0386 Phase 11/12): OCR (engines + OCR languages),
    // then Translation (module), then Media Playback (audio-viz + FFmpeg DTS).
    override fun getExtensions(): List<ExtensionItem> = listOf(
        ExtensionItem.Module(
            id = moduleKey(DeliverableSet.OCR_ENGINES),
            set = DeliverableSet.OCR_ENGINES,
            displayNameRes = R.string.ext_ocr_engines_title,
            descriptionRes = R.string.ext_ocr_engines_desc,
            sizeLabel = moduleSizeLabel(DeliverableSet.OCR_ENGINES),
            section = ExtensionSection.OCR,
            statusFlow = moduleStatusFlow(DeliverableSet.OCR_ENGINES)
        ),
        ExtensionItem.LanguageData(
            id = languageKey("rus"),
            languageCode = "rus",
            displayNameRes = R.string.ext_lang_rus_title,
            descriptionRes = R.string.ext_lang_rus_desc,
            sizeLabel = formatBytes(LANG_SIZE_RUS),
            section = ExtensionSection.OCR,
            statusFlow = languageStatusFlow("rus")
        ),
        ExtensionItem.LanguageData(
            id = languageKey("ukr"),
            languageCode = "ukr",
            displayNameRes = R.string.ext_lang_ukr_title,
            descriptionRes = R.string.ext_lang_ukr_desc,
            sizeLabel = formatBytes(LANG_SIZE_UKR),
            section = ExtensionSection.OCR,
            statusFlow = languageStatusFlow("ukr")
        ),
        ExtensionItem.Module(
            id = moduleKey(DeliverableSet.TRANSLATION),
            set = DeliverableSet.TRANSLATION,
            displayNameRes = R.string.ext_translation_title,
            descriptionRes = R.string.ext_translation_desc,
            sizeLabel = moduleSizeLabel(DeliverableSet.TRANSLATION),
            section = ExtensionSection.TRANSLATION,
            statusFlow = moduleStatusFlow(DeliverableSet.TRANSLATION)
        ),
        ExtensionItem.Module(
            id = moduleKey(DeliverableSet.AUDIO_VISUALIZATIONS),
            set = DeliverableSet.AUDIO_VISUALIZATIONS,
            displayNameRes = R.string.ext_audio_viz_title,
            descriptionRes = R.string.ext_audio_viz_desc,
            sizeLabel = moduleSizeLabel(DeliverableSet.AUDIO_VISUALIZATIONS),
            section = ExtensionSection.MEDIA_PLAYBACK,
            statusFlow = moduleStatusFlow(DeliverableSet.AUDIO_VISUALIZATIONS)
        ),
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

    override fun download(item: ExtensionItem): Flow<DownloadProgress> {
        val statusFlow = statusFlowFor(item)
        return flow {
            val progressFlow = when (item) {
                is ExtensionItem.Module -> downloader.download(item.set)
                is ExtensionItem.LanguageData -> downloadTesseractModel(item.languageCode)
            }
            progressFlow.collect { progress ->
                statusFlow.value = progress.toExtensionStatus()
                emit(progress)
            }
        }
    }

    override suspend fun uninstall(item: ExtensionItem) {
        when (item) {
            is ExtensionItem.Module -> repository.uninstall(item.set)
            is ExtensionItem.LanguageData -> tesseractModelManager.deleteModel(item.languageCode)
        }
        statusFlowFor(item).value = ExtensionStatus.NotInstalled
    }

    // Merges the persisted capability state with any in-flight download so the row reflects both:
    // a live download (or its failure) wins, otherwise the repository's installed/not state shows.
    private fun moduleStatusFlow(set: DeliverableSet): Flow<ExtensionStatus> {
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

    private fun formatBytes(bytes: Long): String =
        String.format(Locale.US, "%.0f MB", bytes / 1024.0 / 1024.0)

    private fun moduleKey(set: DeliverableSet): String = "set_${set.name.lowercase(Locale.ROOT)}"

    private fun languageKey(languageCode: String): String = "lang_ocr_$languageCode"

    companion object {
        // Estimated arm64-v8a download sizes (bytes) shown until a flavor contributes a real
        // descriptor; values mirror temp/S0386_B3_so_staging.md (strategic §5.4).
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
