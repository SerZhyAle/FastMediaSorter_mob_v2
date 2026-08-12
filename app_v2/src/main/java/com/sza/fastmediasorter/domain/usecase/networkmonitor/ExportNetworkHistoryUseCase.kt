package com.sza.fastmediasorter.domain.usecase.networkmonitor

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurement
import com.sza.fastmediasorter.domain.repository.NetworkMeasurementHistoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * S1433: writes the measurement history to a shareable UTF-8 text file.
 *
 * Staged in `cacheDir` and handed out as a `content://` URI through the same FileProvider authority the
 * log export uses, so the file needs no storage permission and the recipient app gets read access for
 * one share only.
 *
 * The file is tab-separated with one header line, which is what makes it useful on arrival: it opens as
 * text anywhere and pastes into a spreadsheet without a parser. An empty history still produces the
 * header line rather than an error - a caller that wants to hide the action already observes the history
 * and can tell it is empty without asking for a file first.
 *
 * Nothing sensitive can reach the file, because nothing sensitive is in the store: strategic §3.2 gives
 * the row no column for an external IP address or a secret.
 */
class ExportNetworkHistoryUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val historyRepository: NetworkMeasurementHistoryRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    // Broad catch is an intentional export-boundary guard: a full cache partition or a misconfigured
    // provider authority surfaces as Result.failure (logged), never a crash on the share action.
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(): Result<Uri> = withContext(ioDispatcher) {
        try {
            val measurements = historyRepository.exportPayload()
            val file = File(context.cacheDir, EXPORT_FILE_NAME)
            file.writeText(buildPayload(measurements), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
            Result.success(uri)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "Network history export failed")
            Result.failure(e)
        }
    }

    private fun buildPayload(measurements: List<NetworkMeasurement>): String {
        val timestampFormat = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US)
        return buildString {
            appendLine(HEADER_LINE)
            measurements.forEach { measurement ->
                appendLine(
                    listOf(
                        timestampFormat.format(Date(measurement.takenAtMillis)),
                        measurement.kind.name,
                        measurement.networkLabel.toSingleLine(),
                        measurement.downMbps?.toString().orEmpty(),
                        measurement.upMbps?.toString().orEmpty(),
                        measurement.latencyMs?.toString().orEmpty(),
                        if (measurement.succeeded) "ok" else "failed",
                        measurement.resultText.toSingleLine()
                    ).joinToString(COLUMN_SEPARATOR)
                )
            }
        }
    }

    // A free-form result that carried a tab or a line break would silently split into extra columns or
    // extra rows, and the reader would see a corrupt table rather than a bad cell.
    private fun String.toSingleLine(): String = replace(WHITESPACE_RUN, " ").trim()

    private companion object {
        const val EXPORT_FILE_NAME = "fastmediasorter_network_measurements.txt"
        const val AUTHORITY_SUFFIX = ".fileprovider"
        const val COLUMN_SEPARATOR = "\t"
        const val TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss"
        const val HEADER_LINE = "takenAt\tkind\tnetwork\tdownMbps\tupMbps\tlatencyMs\toutcome\tresult"
        val WHITESPACE_RUN = Regex("\\s+")
    }
}
