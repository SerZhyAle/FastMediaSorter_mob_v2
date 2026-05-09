package com.sza.fastmediasorter.domain.usecase.link

import android.net.Uri
import com.sza.fastmediasorter.data.link.LinkDownloadWriter
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.link.MediaQualityPreference
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.link.streaming.PipelineOutcome
import com.sza.fastmediasorter.domain.usecase.link.streaming.StreamingPipeline
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import java.io.FileInputStream
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
    private val streamingPipeline: StreamingPipeline,
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
                            is OpenResult.Streaming -> return runStreaming(opened, settings, callbacks)
                            is OpenResult.NotFound -> return Result.Failed.NoMediaFound
                            is OpenResult.Blocked -> when (opened.reason) {
                                BlockedReason.MimeNotAllowed,
                                BlockedReason.NonHttpScheme,
                                BlockedReason.RedirectToNonHttp -> return Result.Failed.MimeBlocked
                                BlockedReason.DrmProtected -> return Result.Failed.DrmBlocked
                                BlockedReason.StreamingDisabled -> return Result.Failed.StreamingDisabled
                                BlockedReason.MuxFailed -> return Result.Failed.MuxFailed(codec = "unknown")
                                BlockedReason.AuthRequired -> return Result.Failed.AuthRequired(
                                    host = url.toHttpUrlOrNull()?.host ?: url,
                                    originalUrl = url,
                                )
                            }
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

    /**
     * S0116 §5.1 pillar I: route a Streaming outcome through the [StreamingPipeline]
     * (Media3 download + MediaMuxer remux on video flavors; no-op `Disabled` on
     * lite/photos) and project the resulting MP4 file into the existing
     * [LinkDownloadWriter] contract so [Saved] / [FellBackToDownloads] reuse the
     * S0003 user-visible UX.
     */
    private suspend fun runStreaming(
        streaming: OpenResult.Streaming,
        settings: AppSettings,
        callbacks: Callbacks,
    ): Result {
        val quality = MediaQualityPreference.fromSettings(
            maxResolution = settings.linkDownloadMaxResolution,
            audioOnly = settings.linkDownloadAudioOnly,
        )
        val outcome = streamingPipeline.fetchAndRemux(
            manifest = streaming.manifest,
            fileName = streaming.tentativeFileName,
            quality = quality,
        ) { read, total ->
            callbacks.onProgress(ProgressState.Downloading(read, total))
        }
        return when (outcome) {
            is PipelineOutcome.Success -> {
                val input = FileInputStream(outcome.file)
                try {
                    val writeResult = writer.writeFromStream(
                        stream = input,
                        mime = outcome.mime,
                        suggestedFileName = streaming.tentativeFileName,
                        resourceId = settings.linkAutoDownloadResourceId,
                        onBytesCopied = { bytes ->
                            callbacks.onProgress(ProgressState.Downloading(bytes, outcome.file.length()))
                        },
                    )
                    val openInPlayer = settings.linkAutoDownloadOpenInPlayer
                    when (writeResult) {
                        is LinkDownloadWriter.WriteResult.Saved -> Result.Saved(
                            resourceLabel = writeResult.resourceLabel,
                            fileName = writeResult.fileName,
                            mime = outcome.mime,
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
                    runCatching { input.close() }
                    runCatching { outcome.file.delete() }
                }
            }
            PipelineOutcome.DrmBlocked -> Result.Failed.DrmBlocked
            PipelineOutcome.Disabled -> Result.Failed.StreamingDisabled
            is PipelineOutcome.MuxFailed -> Result.Failed.MuxFailed(codec = outcome.codec)
            is PipelineOutcome.NetworkError -> mapIoError(outcome.cause)
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
            // S0116 §5.1 pillar I: streaming pipeline-specific terminal outcomes.
            data object DrmBlocked : Failed
            data object StreamingDisabled : Failed
            data class MuxFailed(val codec: String) : Failed
            // S0116 §5.1 pillar L: source returned 401/403 — UI offers WebView sign-in flow.
            data class AuthRequired(val host: String, val originalUrl: String) : Failed
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
