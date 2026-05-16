package com.sza.fastmediasorter.domain.usecase.link

import android.net.Uri
import com.sza.fastmediasorter.data.link.LinkDownloadWriter
import com.sza.fastmediasorter.data.link.LinkUrlCanonicalizer
import com.sza.fastmediasorter.data.link.auth.KnownAuthResources
import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
import com.sza.fastmediasorter.data.link.cookie.LinkDownloadSessionContext
import com.sza.fastmediasorter.data.link.cookie.registrableDomainOrNull
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
    private val urlCanonicalizer: LinkUrlCanonicalizer,
) {

    // S0176: resolves the stored host whose cookies should be applied to the current run.
    // Order: exact host match first; eTLD+1 registrable-domain fallback second.
    // accountId selection is deterministic so the dynamic WebView flow cannot rely on the
    // shared HTTP cookie bridge to mask misses.
    private fun resolveSessionHost(host: String, accountId: String?): String? {
        // Exact match — fastest path, backward compatible.
        val exactCookies = when {
            accountId != null -> cookieStore.loadForAccount(host, accountId)
            else -> @Suppress("DEPRECATION") cookieStore.loadFor(host)
        }
        if (exactCookies.isNotEmpty()) return host

        // eTLD+1 fallback — scan all accounts for a matching registrable domain.
        Timber.d("S0176: exact lookup empty for host=%s — attempting eTLD+1 fallback", host)
        val reg = registrableDomainOrNull(host) ?: return null
        val match = if (accountId != null) {
            cookieStore.listAllAccounts()
                .firstOrNull { (h, e) ->
                    e.accountId == accountId && e.cookieCount > 0 && registrableDomainOrNull(h) == reg
                }
        } else {
            cookieStore.listAllAccounts()
                .firstOrNull { (h, e) -> e.cookieCount > 0 && registrableDomainOrNull(h) == reg }
        }
        return match?.first
    }

    // S0176: uses resolveSessionHost() so that www.instagram.com falls back to the
    // instagram.com stored session when the exact host has no cookies. Returns the
    // resolved stored host so handle() can pass it to markLastUsed().
    // S0182: also loads the pinned User-Agent captured at login time and pushes it
    // into LinkDownloadSessionContext so every downstream stack (yt-dlp, OkHttp)
    // sends the same UA Meta/anti-bot first saw with these cookies.
    // S0190: optional audioOnly hint is propagated from canonicalize() — true for
    // YouTube Music share URLs so the downstream extractor picks an audio-only format.
    private fun applySessionContext(host: String, accountId: String?, audioOnly: Boolean = false): String? {
        Timber.d("S0176: applySessionContext entry host=%s accountId=%s", host, accountId ?: "auto")
        val resolvedHost = resolveSessionHost(host, accountId) ?: return null
        val cookies = when {
            accountId != null -> cookieStore.loadForAccount(resolvedHost, accountId)
            else -> @Suppress("DEPRECATION") cookieStore.loadFor(resolvedHost)
        }
        if (cookies.isNotEmpty()) {
            // S0182: pull UA for the same (resolvedHost, accountId) the cookies came from.
            // If accountId is null, scan accounts under resolvedHost for any active entry
            // that recorded a UA (best-effort fallback used by the auto path).
            val pinnedUa = if (accountId != null) {
                cookieStore.loadUserAgentForAccount(resolvedHost, accountId)
            } else {
                cookieStore.listAccounts(resolvedHost)
                    .asSequence()
                    .mapNotNull { e -> cookieStore.loadUserAgentForAccount(resolvedHost, e.accountId) }
                    .firstOrNull()
            }
            sessionContext.set(resolvedHost, cookies, pinnedUa, audioOnly)
            Timber.d(
                "S0182: applySessionContext bound resolvedHost=%s accountId=%s pinnedUa=%s",
                resolvedHost,
                accountId ?: "auto",
                if (pinnedUa != null) "pinned" else "fallback",
            )
            Timber.i(
                "[S0166] applying stored session: host=%s resolvedHost=%s accountId=%s cookies=%d ua=%s",
                host,
                resolvedHost,
                accountId ?: "auto",
                cookies.size,
                pinnedUa?.take(60) ?: "(none)",
            )
            Timber.d(
                "LinkAutoDownloadCoordinator: session context applied host=%s resolvedHost=%s accountId=%s cookies=%d",
                host,
                resolvedHost,
                accountId ?: "auto",
                cookies.size,
            )
        }
        return resolvedHost
    }

    suspend fun handle(url: String, callbacks: Callbacks, accountId: String? = null): Result {
        val settings = settingsRepository.getSettings().first()
        if (!settings.linkAutoDownloadEnabled) {
            return Result.Failed.Other(IllegalStateException("auto_download_disabled"))
        }

        // S0190: rewrite well-known equivalent URLs (music.youtube.com, m.youtube.com,
        // youtube.com/shorts/<id>) to a canonical form recognised by both yt-dlp and
        // NewPipe upstream. Cookie host resolution below also benefits because the
        // canonical host (youtube.com) is what EncryptedCookieStore is keyed by.
        // The audioOnly hint is set only when the original host was music.youtube.com —
        // propagated into LinkDownloadSessionContext so downstream extractors can pick
        // an audio-only format.
        val canonical = urlCanonicalizer.canonicalize(url)
        Timber.d("S0190: handle entry url=%s accountId=%s audioOnly=%s", url.take(80), accountId ?: "auto", canonical.audioOnly)

        val host = canonical.url.toHttpUrlOrNull()?.host ?: ""
        val appliedSessionHost =
            if (host.isNotBlank()) applySessionContext(host, accountId, canonical.audioOnly) else null
        val result = try {
            handleUrl(url = canonical.url, settings = settings, callbacks = callbacks, accountId = accountId)
        } finally {
            sessionContext.clear()
        }

        if (accountId != null && host.isNotBlank() && (result is Result.Saved || result is Result.FellBackToDownloads)) {
            runCatching { authSessionRepository.markLastUsed(appliedSessionHost ?: host, accountId) }
        }
        return result
    }

    suspend fun handleBatch(urls: List<String>, callbacks: Callbacks): Result {
        val settings = settingsRepository.getSettings().first()
        if (!settings.linkAutoDownloadEnabled) {
            return Result.Failed.Other(IllegalStateException("auto_download_disabled"))
        }

        // S0190: canonicalize before distinct() so YouTube equivalents (e.g.
        // shorts/<id> vs watch?v=<id>) collapse into a single batch item.
        // Batch path is audio-hint-agnostic: a mixed-host batched share with YTMusic
        // entries is a corner case not covered by Phase D (the audioOnly flag is
        // dropped here on purpose).
        val items = urls
            .map(String::trim)
            .filter { it.isNotBlank() }
            .filter { it.toHttpUrlOrNull() != null }
            .map { urlCanonicalizer.canonicalize(it).url }
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
                        val opened = try {
                            strategy.open(url) { read, total ->
                                callbacks.onProgress(ProgressState.Downloading(read, total))
                            }
                        } catch (throwable: Throwable) {
                            // S0186: open() can throw uncaught Throwable (notably PyException
                            // from yt-dlp via Chaquopy). Without this catch the for-loop aborts
                            // and subsequent strategies (NewPipe site, html, dynamic) never get
                            // a chance. CancellationException is re-thrown so user-triggered
                            // cancellation still propagates.
                            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                            Timber.w(throwable, "LinkAutoDownloadCoordinator: open threw for %s", strategy.id)
                            Timber.d("S0186: open() threw for %s, continuing cascade", strategy.id)
                            continue
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