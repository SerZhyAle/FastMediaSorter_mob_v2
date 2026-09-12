package com.sza.fastmediasorter.ui.share.helpers

import android.app.Activity
import android.content.Context
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareTargetOutcome
import com.sza.fastmediasorter.core.share.ShareTargetRegistry
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2142: hands one file the watch sent here to the receiver the watch picked.
 *
 * Outside the activity that hosts it so the surface stays a trampoline, and outside
 * `SendToMenuManager` because there is no menu here: the receiver was already chosen on the watch,
 * the file is already local, and the three content gates that build a menu have nothing left to
 * decide. What is reused is the thing that matters - the receiver's own [ShareTargetHandler], so a
 * file sent from the watch reaches an application by exactly the code path a file sent from this
 * phone does.
 */
@Singleton
class WearSendToErrandManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val registry: ShareTargetRegistry,
    private val handlers: Map<String, @JvmSuppressWildcards ShareTargetHandler>
) {

    /**
     * Runs [receiverId] over the file at [savedPath] and reports whether it left.
     *
     * `false` covers every way the errand can fail on this side - a receiver this build does not
     * declare, a file the provider will not serve, a handler that refused - because the owner is
     * shown one message either way and the log already carries which of them it was.
     */
    suspend fun run(activity: Activity, savedPath: String, receiverId: String): Boolean {
        val target = registry.all().firstOrNull { it.id == receiverId }
        val handler = handlers[receiverId]
        if (target == null || handler == null) {
            Timber.w("Send to from watch: this build declares no receiver %s", receiverId)
            return false
        }
        if (!handler.isSupportedBy(activity)) {
            Timber.w("Send to from watch: %s is not supported by this host", receiverId)
            return false
        }
        val content = contentFor(savedPath) ?: return false
        return runHandler(activity, handler, content, receiverId)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runHandler(
        activity: Activity,
        handler: ShareTargetHandler,
        content: ShareableContent,
        receiverId: String
    ): Boolean = try {
        when (handler.send(activity, content)) {
            is ShareTargetOutcome.Launched, is ShareTargetOutcome.Delivered -> true
            is ShareTargetOutcome.Failed, is ShareTargetOutcome.NotAttempted -> false
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A receiver reaches another application, so every failure it can have arrives from outside
        // this process; none of them may take the trampoline down with it.
        Timber.e(e, "Send to from watch: receiver %s failed to send", receiverId)
        false
    }

    /**
     * A provider URI, never a `file://` one: the receiver is another application, and a raw path is
     * refused there since Android 7 - after the chooser rather than before it, which is the shape of
     * failure ADR-3 exists to avoid.
     */
    private fun contentFor(savedPath: String): ShareableContent? {
        val file = File(savedPath)
        if (!file.isFile) {
            Timber.w("Send to from watch: %s is gone", file.name)
            return null
        }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.onFailure {
            Timber.w(it, "Send to from watch: %s is outside every shared provider path", file.name)
        }.getOrNull() ?: return null
        // A name this phone cannot classify still has bytes and a receiver waiting for them, so it
        // travels as an unclassified binary rather than being refused for a missing category.
        val mediaType = MediaTypeUtils.getMediaType(file.name) ?: MediaType.BINARY_OTHER
        return ShareableContent(
            uris = listOf(uri),
            mime = ShareableContent.mimeForMediaType(file.name, mediaType),
            mediaType = mediaType,
            displayName = file.name
        )
    }
}
