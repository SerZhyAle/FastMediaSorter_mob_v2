package com.sza.fastmediasorter.core.share.handlers

import android.app.Activity
import android.content.ContentResolver
import android.net.Uri
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareTargetOutcome
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.domain.usecase.SendFileToWatchUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Paired-watch receiver of the «Send to..» menu (S1884 phase 04).
 *
 * Unlike every package-backed receiver this one does the work itself and knows how it ended, so it
 * reports each of the six outcomes with its own wording instead of the dispatcher's generic failure
 * text (ADR-4). It shows nothing itself - the dispatcher owns the message.
 */
class WatchShareTargetHandler @Inject constructor(
    private val sendFileToWatch: SendFileToWatchUseCase,
) : ShareTargetHandler {

    override val targetId: String = ID

    override suspend fun send(activity: Activity, content: ShareableContent): ShareTargetOutcome {
        val path = resolveLocalPath(activity, content)
            ?: return ShareTargetOutcome.Failed()
        return sendFileToWatch(
            path = path,
            displayName = content.displayName ?: File(path).name,
            mediaType = content.mediaType,
        ).toShareOutcome(activity)
    }

    /**
     * The transport takes a filesystem path, but the surfaces disagree on what they hand over: most
     * set [ShareableContent.mediaFile], while browse multi-select and camera capture pass only a
     * FileProvider `content://` Uri, whose `path` is a provider-relative segment and not a real file.
     * Staging that Uri into the cache is the only branch that keeps the receiver alive on those two.
     */
    private suspend fun resolveLocalPath(activity: Activity, content: ShareableContent): String? {
        val declared = content.mediaFile?.path?.takeIf { File(it).isFile }
        val uri = content.uris.firstOrNull()
        return when {
            declared != null -> declared
            uri == null -> null
            uri.scheme == ContentResolver.SCHEME_FILE -> uri.path?.takeIf { File(it).isFile }
            else -> stageForTransfer(activity, uri, content.displayName)
        }
    }

    private suspend fun stageForTransfer(activity: Activity, uri: Uri, displayName: String?): String? =
        withContext(Dispatchers.IO) {
            val dir = File(activity.cacheDir, CACHE_DIR).apply { mkdirs() }
            // One staged copy at a time. Nothing here is user data - it is a byte-for-byte duplicate of
            // a file that still exists at its source - so the directory is emptied on the way in rather
            // than growing by one copy per send with no owner to ever clear it.
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, displayName ?: DEFAULT_NAME)
            try {
                activity.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input).use { source ->
                        target.outputStream().use { sink -> source.copyTo(sink) }
                    }
                }
                target.absolutePath
            } catch (e: IOException) {
                Timber.w(e, "Send to watch: could not stage the shared file for transfer")
                null
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Send to watch: the shared file has no readable stream")
                null
            }
        }

    private fun SendFileToWatchUseCase.Outcome.toShareOutcome(activity: Activity): ShareTargetOutcome =
        when (this) {
            is SendFileToWatchUseCase.Outcome.Opened ->
                ShareTargetOutcome.Delivered(activity.getString(R.string.share_target_watch_opened))
            is SendFileToWatchUseCase.Outcome.WatchAppNotOpen ->
                ShareTargetOutcome.Failed(R.string.share_target_watch_app_not_open)
            is SendFileToWatchUseCase.Outcome.WatchUnavailable ->
                ShareTargetOutcome.Failed(R.string.share_target_watch_unavailable)
            is SendFileToWatchUseCase.Outcome.NoReply ->
                ShareTargetOutcome.Failed(R.string.share_target_watch_no_reply)
            is SendFileToWatchUseCase.Outcome.UnsupportedType ->
                ShareTargetOutcome.Failed(R.string.share_target_watch_unsupported)
            is SendFileToWatchUseCase.Outcome.TooLarge ->
                ShareTargetOutcome.Failed(R.string.share_target_watch_too_large)
            is SendFileToWatchUseCase.Outcome.Error -> {
                Timber.w("Send to watch: the watch answered with an unknown outcome %s", message)
                ShareTargetOutcome.Failed()
            }
        }

    companion object {
        const val ID = "watch"
        private const val CACHE_DIR = "send_to_watch"
        private const val DEFAULT_NAME = "shared_file"
    }
}
