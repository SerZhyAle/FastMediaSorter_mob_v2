package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamingCacheEntry
import com.sza.fastmediasorter.domain.model.CleanupPromptRequest
import timber.log.Timber

/**
 * Shows the "Keep / Delete / Don't ask again" dialog after an offloaded file
 * finishes playing (player exit or file switch). All three choices are surfaced;
 * the third ("Don't ask again") is session-scope only - it does NOT modify the
 * persisted Settings cleanup mode. For that the user opens Settings.
 *
 * The [onDelete] callback deletes the local copy and the DB entry.
 * The [onKeep] callback is a no-op (file stays until TTL GC or manual clear).
 * The [onSuppressForSession] callback lets the caller skip prompts for the rest
 * of this session.
 */
object StreamingCacheCleanupHelper {

    fun showPrompt(
        context: Context,
        request: CleanupPromptRequest,
        onDelete: (StreamingCacheEntry) -> Unit,
        onKeep: (StreamingCacheEntry) -> Unit,
        onSuppressForSession: () -> Unit
    ) {
        val entry = request.entry
        Timber.d("StreamingCacheCleanupHelper: showing prompt for %s", entry.localPath)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.cleanup_prompt_title)
            .setMessage(
                // %1$s = filename, %2$s = human-readable size (string requires 2 args)
                context.getString(
                    R.string.cleanup_prompt_body_format,
                    entry.localPath.substringAfterLast("/"),
                    android.text.format.Formatter.formatShortFileSize(context, entry.sizeBytes)
                )
            )
            .setPositiveButton(R.string.cleanup_keep) { _, _ ->
                Timber.d("StreamingCacheCleanupHelper: user chose Keep")
                onKeep(entry)
            }
            .setNegativeButton(R.string.cleanup_delete) { _, _ ->
                Timber.d("StreamingCacheCleanupHelper: user chose Delete")
                onDelete(entry)
            }
            .setNeutralButton(R.string.cleanup_dont_ask) { _, _ ->
                Timber.d("StreamingCacheCleanupHelper: user suppressed prompts for session")
                onSuppressForSession()
                onKeep(entry)
            }
            .setCancelable(false)
            .show()
    }
}
