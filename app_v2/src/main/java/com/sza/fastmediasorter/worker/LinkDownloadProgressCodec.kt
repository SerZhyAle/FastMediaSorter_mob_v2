package com.sza.fastmediasorter.worker

import androidx.work.Data
import androidx.work.workDataOf
import com.sza.fastmediasorter.domain.usecase.link.LinkAutoDownloadCoordinator

/**
 * S0202: wire format for transporting [LinkAutoDownloadCoordinator.ProgressState]
 * between [LinkDownloadWorker] (producer, via `setProgress`) and the UI observer
 * subscribed to `WorkInfo.progress` inside `ReceiveShareActivity`.
 *
 * Stable string keys are used so the encoded payload survives WorkManager's
 * own serialization round-trip and remains readable across process boundaries.
 *
 * `decode` returns `null` for missing or unknown payloads so callers can ignore
 * spurious empty `Data.EMPTY` ticks WorkManager emits between progress writes.
 */
object LinkDownloadProgressCodec {

    private const val KEY_KIND = "prog_kind"
    private const val KEY_BYTES_READ = "prog_bytes_read"
    private const val KEY_BYTES_TOTAL = "prog_bytes_total"
    private const val KEY_ITEM_INDEX = "prog_item_index"
    private const val KEY_ITEM_COUNT = "prog_item_count"

    private const val KIND_PROBING = "probing"
    private const val KIND_ANALYZING = "analyzing"
    private const val KIND_DOWNLOADING = "downloading"
    private const val KIND_BATCH = "batch"

    private const val UNKNOWN_TOTAL: Long = -1L

    fun encode(state: LinkAutoDownloadCoordinator.ProgressState): Data {
        return when (state) {
            LinkAutoDownloadCoordinator.ProgressState.Probing -> workDataOf(
                KEY_KIND to KIND_PROBING,
            )
            LinkAutoDownloadCoordinator.ProgressState.AnalyzingPage -> workDataOf(
                KEY_KIND to KIND_ANALYZING,
            )
            is LinkAutoDownloadCoordinator.ProgressState.Downloading -> workDataOf(
                KEY_KIND to KIND_DOWNLOADING,
                KEY_BYTES_READ to state.bytesRead,
                KEY_BYTES_TOTAL to (state.total ?: UNKNOWN_TOTAL),
            )
            is LinkAutoDownloadCoordinator.ProgressState.BatchDownloading -> workDataOf(
                KEY_KIND to KIND_BATCH,
                KEY_BYTES_READ to state.bytesRead,
                KEY_BYTES_TOTAL to (state.total ?: UNKNOWN_TOTAL),
                KEY_ITEM_INDEX to state.itemIndex,
                KEY_ITEM_COUNT to state.itemCount,
            )
        }
    }

    fun decode(data: Data): LinkAutoDownloadCoordinator.ProgressState? {
        val kind = data.getString(KEY_KIND) ?: return null
        return when (kind) {
            KIND_PROBING -> LinkAutoDownloadCoordinator.ProgressState.Probing
            KIND_ANALYZING -> LinkAutoDownloadCoordinator.ProgressState.AnalyzingPage
            KIND_DOWNLOADING -> {
                val bytesRead = data.getLong(KEY_BYTES_READ, 0L)
                val rawTotal = data.getLong(KEY_BYTES_TOTAL, UNKNOWN_TOTAL)
                LinkAutoDownloadCoordinator.ProgressState.Downloading(
                    bytesRead = bytesRead,
                    total = rawTotal.takeIf { it > 0L },
                )
            }
            KIND_BATCH -> {
                val bytesRead = data.getLong(KEY_BYTES_READ, 0L)
                val rawTotal = data.getLong(KEY_BYTES_TOTAL, UNKNOWN_TOTAL)
                val itemIndex = data.getInt(KEY_ITEM_INDEX, 0)
                val itemCount = data.getInt(KEY_ITEM_COUNT, 0)
                LinkAutoDownloadCoordinator.ProgressState.BatchDownloading(
                    itemIndex = itemIndex,
                    itemCount = itemCount,
                    itemTitle = null,
                    bytesRead = bytesRead,
                    total = rawTotal.takeIf { it > 0L },
                )
            }
            else -> null
        }
    }
}
