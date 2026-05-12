package com.sza.fastmediasorter.domain.usecase.link

import android.net.Uri
import com.sza.fastmediasorter.data.link.LinkDownloadWriter
import com.sza.fastmediasorter.data.link.auth.KnownAuthResources
import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
import com.sza.fastmediasorter.data.link.cookie.LinkDownloadSessionContext
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.link.MediaQualityPreference
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
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

@Singleton
class LinkAutoDownloadCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val registry: LinkExtractionRegistry,
    private val writer: LinkDownloadWriter,
    private val streamingPipeline: StreamingPipeline,
    private val authSessionRepository: AuthSessionRepository,
    private val sessionContext: LinkDownloadSessionContext,
    private val cookieStore: EncryptedCookieStore,
) {

    private fun applySessionContext(host: String, accountId: String?) {
        val cookies = when {
            accountId != null -> cookieStore.loadForAccount(host, accountId)
            else -> @Suppress("DEPRECATION") cookieStore.loadFor(host)
        }
        if (cookies.isNotEmpty()) {
            sessionContext.set(host, cookies)
            Timber.i(
                "[S0166] applying stored session: host=%s accountId=%s cookies=%d",
                host,
                accountId ?: "auto",
                cookies.size,
            )
            Timber.d(
                "LinkAutoDownloadCoordinator: session context applied host=%s accountId=%s cookies=%d",
                host,
                accountId ?: "auto",
                cookies.size,
            )
        }
    }

    suspend fun handle(url: String, callbacks: Callbacks, accountId: String? = null): Result {
        val settings = settingsRepository.getSettings().first()
        if (!settings.linkAutoDownloadEnabled) {
            return Result.Failed.Other(IllegalStateException("auto_download_disabled"))
        }

        val host = url.toHttpUrlOrNull()?.host ?: ""
        if (host.isNotBlank()) applySessionContext(host, accountId)
        val result = try {
            handleUrl(url = url, settings = settings, callbacks = callbacks, accountId = accountId)
        } finally {
            sessionContext.clear()
        }

        if (accountId != null && host.isNotBlank() && (result is Result.Saved || result is Result.FellBackToDownloads)) {
            runCatching { authSessionRepository.markLastUsed(host, accountId) }
        }
        return result
    }

    suspend fun handleBatch(urls: List<String>, callbacks: Callbacks): Result {
        val settings = settingsRepository.getSettings().first()
        if (!settings.linkAutoDownloadEnabled) {
            return Result.Failed.Other(IllegalStateException("auto_download_disabled"))
        }

        val items = urls
            .map(String::trim)
            .filter { it.isNotBlank() }
            .filter { it.toHttpUrlOrNull() != null }
            .distinct()
            .map { SiteBatchItem(url = it) }

        if (items.isEmpty()) return Result.Failed.NoMediaFound
        if (items.size == 1) return handleUrl(items.first().url, settings, callbacks)

        return runBatch(
            batch = OpenResult.Batch(items = items),
            settings = settings,
            callbacks = callbacks,
        )
    }

    private suspend fun handleUrl(
        url: String,
        settings: AppSettings,
        callbacks: Callbacks,
        accountId: String? = null,
    ): Result {
        callbacks.onProgress(ProgressState.Probing)

        var openedStream: OpenResult.Stream? = null
        var socialPreviewHost: String? = null
        try {
            for (strategy in registry.ordered()) {
                callbacks.onProgress(ProgressState.Probing)
                val probe = try {
                    strategy.probe(url)
                } catch (throwable: Throwable) {
                    if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                    Timber.w(throwable, "LinkAutoDownloadCoordinator: probe threw for %s", strategy.id)
                    continue
                }

                when (probe) {
                    is ProbeResult.Applicable -> {
                        callbacks.onProgress(
                            if (strategy.id == DYNAMIC_STRATEGY_ID) {
                                ProgressState.AnalyzingPage
                            } else {
                                ProgressState.Downloading(0L, probe.tentativeSizeBytes)
                            },
                        )
                        val opened = strategy.open(url) { read, total ->
                            callbacks.onProgress(ProgressState.Downloading(read, total))
                        }
                        val urlHost = url.toHttpUrlOrNull()?.host ?: ""
                        if (KnownAuthResources.isPreviewSensitiveHost(urlHost)) {
                            Timber.d(
                                "S0151-diag: host=%s strategy=%s sessionApplied=%s outcome=%s",
                                urlHost,
                                strategy.id,
                                accountId != null,
                                outcomeKindOf(opened),
                            )
                        }
                        when (opened) {
                            is OpenResult.Stream -> {
                                openedStream = opened
                                break
                            }
                            is OpenResult.Streaming -> return runStreaming(opened, settings, callbacks)
                            is OpenResult.Batch -> return runBatch(opened, settings, callbacks)
                            is OpenResult.SocialPreviewOnly -> {
                                Timber.i(
                                    "[S0166] preview-only result: host=%s strategy=%s accountId=%s",
                                    opened.host,
                                    strategy.id,
                                    accountId ?: "none",
                                )
                                if (socialPreviewHost == null) socialPreviewHost = opened.host
                                continue
                            }
                            is OpenResult.NotFound -> continue
                            is OpenResult.Blocked -> when (opened.reason) {
                                BlockedReason.MimeNotAllowed,
                                BlockedReason.NonHttpScheme,
                                BlockedReason.RedirectToNonHttp,
                                -> return Result.Failed.MimeBlocked

                                BlockedReason.DrmProtected -> return Result.Failed.DrmBlocked
                                BlockedReason.StreamingDisabled -> return Result.Failed.StreamingDisabled
                                BlockedReason.MuxFailed -> return Result.Failed.MuxFailed(codec = "unknown")
                                BlockedReason.AuthRequired -> {
                                    Timber.i(
                                        "[S0166] login wall detected: host=%s strategy=%s accountId=%s",
                                        url.toHttpUrlOrNull()?.host ?: url,
                                        strategy.id,
                                        accountId ?: "none",
                                    )
                                    return Result.Failed.AuthRequired(
                                        host = url.toHttpUrlOrNull()?.host ?: url,
                                        originalUrl = url,
                                    )
                                }
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

            val stream = openedStream
            if (stream == null) {
                val previewHost = socialPreviewHost
                if (previewHost != null) {
                    val hadSession = runCatching { authSessionRepository.hasAnySession(previewHost) }.getOrDefault(false)
                    val accountDisplayName = accountId?.let { id ->
                        runCatching {
                            authSessionRepository.listAccountsForHost(previewHost)
                                .firstOrNull { it.accountId == id }
                                ?.displayName
                        }.getOrNull()
                    }
                    return Result.Failed.SocialPreviewOnly(
                        host = previewHost,
                        originalUrl = url,
                        hadExistingSession = hadSession,
                        accountId = accountId,
                        accountDisplayName = accountDisplayName,
                    )
                }
                Timber.i(
                    "[S0166] no real media found after analysis: host=%s accountId=%s",
                    url.toHttpUrlOrNull()?.host ?: url,
                    accountId ?: "none",
                )
                return Result.Failed.NoMediaFound
            }
            return writeStreamResult(stream = stream, settings = settings, callbacks = callbacks)
        } finally {
            openedStream?.close?.invoke()
        }
    }

    private suspend fun writeStreamResult(
        stream: OpenResult.Stream,
        settings: AppSettings,
        callbacks: Callbacks,
    ): Result {
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
            is LinkDownloadWriter.WriteResult.Saved -> {
                Timber.i(
                    "[S0166] real media saved: file=%s mime=%s resource=%s",
                    writeResult.fileName,
                    stream.mime,
                    writeResult.resourceLabel,
                )
                Result.Saved(
                    resourceLabel = writeResult.resourceLabel,
                    fileName = writeResult.fileName,
                    mime = stream.mime,
                    openInPlayerUri = writeResult.destinationUri.takeIf { openInPlayer },
                )
            }
            is LinkDownloadWriter.WriteResult.FellBackToDownloads -> {
                Timber.i(
                    "[S0166] real media saved via fallback: file=%s reason=%s",
                    writeResult.fileName,
                    writeResult.reason,
                )
                Result.FellBackToDownloads(
                    fileName = writeResult.fileName,
                    reason = when (writeResult.reason) {
                        LinkDownloadWriter.FallbackReason.NoResourceConfigured -> FallbackReason.NoResourceConfigured
                        LinkDownloadWriter.FallbackReason.ResourceUnavailable -> FallbackReason.ResourceUnavailable
                        LinkDownloadWriter.FallbackReason.ResourceWriteFailed -> FallbackReason.ResourceWriteFailed
                    },
                    openInPlayerUri = writeResult.destinationUri.takeIf { openInPlayer },
                )
            }
            is LinkDownloadWriter.WriteResult.Failed -> Result.Failed.Other(writeResult.cause)
            is LinkDownloadWriter.WriteResult.Corrupted -> {
                Timber.i(
                    "[S0166] download rejected as corrupted: kind=%s bytes=%d mime=%s",
                    writeResult.sniffedKind,
                    writeResult.bytesWritten,
                    stream.mime,
                )
                Result.Failed.DownloadCorrupted
            }
        }
    }

    private suspend fun runBatch(
        batch: OpenResult.Batch,
        settings: AppSettings,
        callbacks: Callbacks,
    ): Result {
        if (batch.items.isEmpty()) return Result.Failed.NoMediaFound

        val failures = mutableListOf<Result.BatchFailure>()
        var successCount = 0
        batch.items.forEachIndexed { index, item ->
            val itemIndex = index + 1
            callbacks.onProgress(
                ProgressState.BatchDownloading(
                    itemIndex = itemIndex,
                    itemCount = batch.items.size,
                    itemTitle = item.title,
                    bytesRead = 0L,
                    total = null,
                ),
            )

            val itemCallbacks = object : Callbacks {
                override fun onProgress(state: ProgressState) {
                    when (state) {
                        ProgressState.Probing,
                        ProgressState.AnalyzingPage,
                        -> callbacks.onProgress(
                            ProgressState.BatchDownloading(
                                itemIndex = itemIndex,
                                itemCount = batch.items.size,
                                itemTitle = item.title,
                                bytesRead = 0L,
                                total = null,
                            ),
                        )

                        is ProgressState.Downloading -> callbacks.onProgress(
                            ProgressState.BatchDownloading(
                                itemIndex = itemIndex,
                                itemCount = batch.items.size,
                                itemTitle = item.title,
                                bytesRead = state.bytesRead,
                                total = state.total,
                            ),
                        )

                        is ProgressState.BatchDownloading -> callbacks.onProgress(state)
                    }
                }
            }

            when (val itemResult = handleUrl(item.url, settings, itemCallbacks)) {
                is Result.Saved,
                is Result.FellBackToDownloads,
                -> successCount += 1

                is Result.BatchCompleted -> failures += Result.BatchFailure(
                    title = item.title ?: item.url,
                    failure = Result.Failed.Other(IllegalStateException("nested_batch_not_supported")),
                )

                is Result.Failed -> failures += Result.BatchFailure(
                    title = item.title ?: item.url,
                    failure = itemResult,
                )
            }
        }

        return Result.BatchCompleted(
            summary = Result.BatchSummary(
                label = batch.label,
                totalItems = batch.items.size,
                successCount = successCount,
                failures = failures,
            ),
        )
    }

    private fun mapIoError(cause: Throwable): Result.Failed {
        return when (cause) {
            is UnknownHostException, is ConnectException -> Result.Failed.NoNetwork
            is SocketTimeoutException -> Result.Failed.Timeout
            is IOException -> Result.Failed.NoNetwork
            else -> Result.Failed.Other(cause)
        }
    }

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
                        is LinkDownloadWriter.WriteResult.Corrupted -> {
                            Timber.i(
                                "[S0166] download rejected as corrupted: kind=%s bytes=%d mime=%s",
                                writeResult.sniffedKind,
                                writeResult.bytesWritten,
                                outcome.mime,
                            )
                            Result.Failed.DownloadCorrupted
                        }
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

        data class BatchCompleted(
            val summary: BatchSummary,
        ) : Result

        data class BatchSummary(
            val label: String?,
            val totalItems: Int,
            val successCount: Int,
            val failures: List<BatchFailure>,
        ) {
            val failureCount: Int
                get() = failures.size
        }

        data class BatchFailure(
            val title: String,
            val failure: Failed,
        )

        sealed interface Failed : Result {
            data object NoNetwork : Failed
            data object Timeout : Failed
            data object NoMediaFound : Failed

            /** S0170 BUG-2: media URL was found but the downloaded bytes are not a usable file. */
            data object DownloadCorrupted : Failed
            data object MimeBlocked : Failed
            data object DrmBlocked : Failed
            data object StreamingDisabled : Failed
            data class MuxFailed(val codec: String) : Failed
            data class AuthRequired(val host: String, val originalUrl: String) : Failed
            data class SocialPreviewOnly(
                val host: String,
                val originalUrl: String,
                val hadExistingSession: Boolean,
                val accountId: String? = null,
                val accountDisplayName: String? = null,
            ) : Failed
            data class Other(val cause: Throwable) : Failed
        }
    }

    enum class FallbackReason {
        NoResourceConfigured,
        ResourceUnavailable,
        ResourceWriteFailed,
    }

    sealed interface ProgressState {
        data object Probing : ProgressState
        data object AnalyzingPage : ProgressState
        data class Downloading(val bytesRead: Long, val total: Long?) : ProgressState
        data class BatchDownloading(
            val itemIndex: Int,
            val itemCount: Int,
            val itemTitle: String?,
            val bytesRead: Long,
            val total: Long?,
        ) : ProgressState
    }

    private companion object {
        const val DYNAMIC_STRATEGY_ID = "dynamic"

        fun outcomeKindOf(opened: OpenResult): String = when (opened) {
            is OpenResult.Stream -> "stream"
            is OpenResult.Batch -> "batch(${opened.items.size})"
            is OpenResult.SocialPreviewOnly -> "social-preview-only"
            is OpenResult.NotFound -> "not-found"
            is OpenResult.Blocked -> when (opened.reason) {
                BlockedReason.NonHttpScheme, BlockedReason.RedirectToNonHttp -> "non-http"
                else -> "blocked(${opened.reason})"
            }
            is OpenResult.Streaming -> "streaming"
            is OpenResult.Error -> "error"
        }
    }
}