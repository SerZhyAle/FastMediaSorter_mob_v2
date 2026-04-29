package com.sza.fastmediasorter.domain.usecase.link

import android.net.Uri
import com.sza.fastmediasorter.data.link.LinkDownloadWriter
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0003: orchestrates URL → file pipeline.
 *
 * Walks [LinkExtractionRegistry.ordered] strategies, accepts the first `Applicable`
 * probe, streams the response into [LinkDownloadWriter], and projects the writer
 * outcome into a user-friendly [Result] (auto-open URI is gated by user preference).
 */
@Singleton
class LinkAutoDownloadCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val registry: LinkExtractionRegistry,
    private val writer: LinkDownloadWriter,
) {

    suspend fun handle(url: String, callbacks: Callbacks): Result {
        val settings = settingsRepository.getSettings().first()
        if (!settings.linkAutoDownloadEnabled) {
            return Result.Failed.Other(IllegalStateException("auto_download_disabled"))
        }
        callbacks.onProgress(ProgressState.Probing)

        var openedStream: OpenResult.Stream? = null
        try {
            for (strategy in registry.ordered()) {
                callbacks.onProgress(ProgressState.Probing)
                val probe = try {
                    strategy.probe(url)
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    Timber.w(t, "LinkAutoDownloadCoordinator: probe threw for %s", strategy.id)
                    continue
                }
                when (probe) {
                    is ProbeResult.Applicable -> {
                        callbacks.onProgress(ProgressState.Downloading(0L, probe.tentativeSizeBytes))
                        when (val opened = strategy.open(url) { read, total ->
                            callbacks.onProgress(ProgressState.Downloading(read, total))
                        }) {
                            is OpenResult.Stream -> {
                                openedStream = opened
                                break
                            }
                            is OpenResult.NotFound -> return Result.Failed.NoMediaFound
                            is OpenResult.Blocked -> return Result.Failed.MimeBlocked
                            is OpenResult.Error -> return mapIoError(opened.cause)
                        }
                    }
                    ProbeResult.NotApplicable -> Unit
                    is ProbeResult.TransientError -> {
                        Timber.w(probe.cause, "LinkAutoDownloadCoordinator: probe transient for %s", strategy.id)
                    }
                }
            }

            val stream = openedStream ?: return Result.Failed.NoMediaFound

            val writeResult = writer.writeFromStream(
                stream = stream.body,
                mime = stream.mime,
                suggestedFileName = stream.fileName,
                resourceId = settings.linkAutoDownloadResourceId,
                onBytesCopied = { bytes ->
                    callbacks.onProgress(ProgressState.Downloading(bytes, stream.contentLength))
                },
            )

            val openInPlayer = settings.linkAutoDownloadOpenInPlayer
            return when (writeResult) {
                is LinkDownloadWriter.WriteResult.Saved -> Result.Saved(
                    resourceLabel = writeResult.resourceLabel,
                    fileName = writeResult.fileName,
                    mime = stream.mime,
                    openInPlayerUri = writeResult.destinationUri.takeIf { openInPlayer },
                )
                is LinkDownloadWriter.WriteResult.FellBackToDownloads -> Result.FellBackToDownloads(
                    fileName = writeResult.fileName,
                    reason = when (writeResult.reason) {
                        LinkDownloadWriter.FallbackReason.NoResourceConfigured -> FallbackReason.NoResourceConfigured
                        LinkDownloadWriter.FallbackReason.ResourceUnavailable -> FallbackReason.ResourceUnavailable
                        LinkDownloadWriter.FallbackReason.ResourceWriteFailed -> FallbackReason.ResourceWriteFailed
                    },
                    openInPlayerUri = writeResult.destinationUri.takeIf { openInPlayer },
                )
                is LinkDownloadWriter.WriteResult.Failed -> Result.Failed.Other(writeResult.cause)
            }
        } finally {
            openedStream?.close?.invoke()
        }
    }

    private fun mapIoError(cause: Throwable): Result.Failed {
        return when (cause) {
            is UnknownHostException, is ConnectException -> Result.Failed.NoNetwork
            is SocketTimeoutException -> Result.Failed.Timeout
            is IOException -> Result.Failed.NoNetwork
            else -> Result.Failed.Other(cause)
        }
    }

    interface Callbacks {
        fun onProgress(state: ProgressState)
    }

    sealed interface Result {
        data class Saved(
            val resourceLabel: String,
            val fileName: String,
            val mime: String,
            val openInPlayerUri: Uri?,
        ) : Result

        data class FellBackToDownloads(
            val fileName: String,
            val reason: FallbackReason,
            val openInPlayerUri: Uri?,
        ) : Result

        sealed interface Failed : Result {
            object NoNetwork : Failed
            object Timeout : Failed
            object NoMediaFound : Failed
            object MimeBlocked : Failed
            data class Other(val cause: Throwable) : Failed
        }
    }

    enum class FallbackReason {
        NoResourceConfigured,
        ResourceUnavailable,
        ResourceWriteFailed,
    }

    sealed interface ProgressState {
        object Probing : ProgressState
        data class Downloading(val bytesRead: Long, val total: Long?) : ProgressState
    }
}
